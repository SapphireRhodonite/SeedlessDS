
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>
#include <android/log.h>
#include "rc_client.h"
#include "rc_api_request.h"
#include "rc_hash.h"

#define TAG "RAClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern uint32_t ra_read_memory(uint32_t addr, uint8_t *buf, uint32_t n);

static JavaVM *g_vm = NULL;
static rc_client_t *g_client = NULL;
static jclass g_cls = NULL;
static jmethodID mid_serverCall = NULL, mid_onLogin = NULL, mid_onLoad = NULL, mid_onEvent = NULL;
static jmethodID mid_onLbEvent = NULL, mid_onLbEntries = NULL;

static int g_hash_fd = -1;
typedef struct { int64_t pos; } ra_fr_t;

static void *ra_fr_open(const char *path) {
    (void) path;
    if (g_hash_fd < 0) return NULL;
    ra_fr_t *h = (ra_fr_t *) malloc(sizeof(ra_fr_t));
    if (h) h->pos = 0;
    return h;
}
static void ra_fr_seek(void *handle, int64_t offset, int origin) {
    ra_fr_t *h = (ra_fr_t *) handle;
    if (!h) return;
    if (origin == SEEK_SET) h->pos = offset;
    else if (origin == SEEK_CUR) h->pos += offset;
    else if (origin == SEEK_END) {
        off_t end = (g_hash_fd >= 0) ? lseek(g_hash_fd, 0, SEEK_END) : 0;
        h->pos = (int64_t) end + offset;
    }
}
static int64_t ra_fr_tell(void *handle) {
    ra_fr_t *h = (ra_fr_t *) handle;
    return h ? h->pos : 0;
}
static size_t ra_fr_read(void *handle, void *buffer, size_t requested) {
    ra_fr_t *h = (ra_fr_t *) handle;
    if (!h || g_hash_fd < 0) return 0;
    ssize_t n = pread(g_hash_fd, buffer, requested, (off_t) h->pos);
    if (n > 0) h->pos += n;
    return (n > 0) ? (size_t) n : 0;
}
static void ra_fr_close(void *handle) { free(handle); }

static struct rc_hash_filereader g_ra_filereader = {
    .open = ra_fr_open, .seek = ra_fr_seek, .tell = ra_fr_tell,
    .read = ra_fr_read, .close = ra_fr_close,
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    g_vm = vm;
    return JNI_VERSION_1_6;
}

static JNIEnv *ra_env(void) {
    JNIEnv *env = NULL;
    if (g_vm) (*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6);
    return env;
}

static uint32_t rc_read(uint32_t address, uint8_t *buffer, uint32_t num_bytes, rc_client_t *client) {
    (void) client;
    return ra_read_memory(address, buffer, num_bytes);
}

typedef struct { rc_client_server_callback_t cb; void *data; } ra_call_t;

static void rc_server_call(const rc_api_request_t *request, rc_client_server_callback_t callback,
                           void *callback_data, rc_client_t *client) {
    (void) client;
    JNIEnv *env = ra_env();
    if (!env || !g_cls) { if (callback) callback(NULL, callback_data); return; }
    ra_call_t *h = (ra_call_t *) malloc(sizeof(ra_call_t));
    if (!h) { if (callback) callback(NULL, callback_data); return; }
    h->cb = callback; h->data = callback_data;
    jstring url = (*env)->NewStringUTF(env, request->url ? request->url : "");
    jstring post = request->post_data ? (*env)->NewStringUTF(env, request->post_data) : NULL;
    jstring ctype = request->content_type ? (*env)->NewStringUTF(env, request->content_type) : NULL;
    (*env)->CallStaticVoidMethod(env, g_cls, mid_serverCall, (jlong) (uintptr_t) h, url, post, ctype);
    (*env)->DeleteLocalRef(env, url);
    if (post) (*env)->DeleteLocalRef(env, post);
    if (ctype) (*env)->DeleteLocalRef(env, ctype);
}

static void rc_lb_event(JNIEnv *env, const rc_client_event_t *event) {
    jint type = (jint) event->type;
    jint lbId = 0, trackerId = 0, rank = 0, total = 0;
    jstring title = NULL, value = NULL, best = NULL;
    switch (event->type) {
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_SHOW:
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_UPDATE:
            if (event->leaderboard_tracker) {
                trackerId = (jint) event->leaderboard_tracker->id;
                value = (*env)->NewStringUTF(env, event->leaderboard_tracker->display);
            }
            break;
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_HIDE:
            if (event->leaderboard_tracker) trackerId = (jint) event->leaderboard_tracker->id;
            break;
        case RC_CLIENT_EVENT_LEADERBOARD_STARTED:
        case RC_CLIENT_EVENT_LEADERBOARD_FAILED:
        case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED:
            if (event->leaderboard) {
                lbId = (jint) event->leaderboard->id;
                title = (*env)->NewStringUTF(env, event->leaderboard->title ? event->leaderboard->title : "");
                if (event->leaderboard->tracker_value)
                    value = (*env)->NewStringUTF(env, event->leaderboard->tracker_value);
            }
            break;
        case RC_CLIENT_EVENT_LEADERBOARD_SCOREBOARD:
            if (event->leaderboard_scoreboard) {
                const rc_client_leaderboard_scoreboard_t *sb = event->leaderboard_scoreboard;
                lbId = (jint) sb->leaderboard_id;
                rank = (jint) sb->new_rank;
                total = (jint) sb->num_entries;
                value = (*env)->NewStringUTF(env, sb->submitted_score);
                best = (*env)->NewStringUTF(env, sb->best_score);
                const rc_client_leaderboard_t *lb = rc_client_get_leaderboard_info(g_client, sb->leaderboard_id);
                if (lb && lb->title) title = (*env)->NewStringUTF(env, lb->title);
            }
            break;
        default: break;
    }
    (*env)->CallStaticVoidMethod(env, g_cls, mid_onLbEvent, type, lbId, trackerId, rank, total, title, value, best);
    if (title) (*env)->DeleteLocalRef(env, title);
    if (value) (*env)->DeleteLocalRef(env, value);
    if (best) (*env)->DeleteLocalRef(env, best);
}

static void rc_event(const rc_client_event_t *event, rc_client_t *client) {
    (void) client;
    JNIEnv *env = ra_env();
    if (!env || !g_cls) return;
    switch (event->type) {
        case RC_CLIENT_EVENT_LEADERBOARD_STARTED:
        case RC_CLIENT_EVENT_LEADERBOARD_FAILED:
        case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED:
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_SHOW:
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_HIDE:
        case RC_CLIENT_EVENT_LEADERBOARD_TRACKER_UPDATE:
        case RC_CLIENT_EVENT_LEADERBOARD_SCOREBOARD:
            rc_lb_event(env, event);
            return;
        default: break;
    }
    jint type = (jint) event->type, id = 0, points = 0;
    jstring title = NULL, desc = NULL, badge = NULL;
    if (event->achievement) {
        const rc_client_achievement_t *a = event->achievement;
        id = (jint) a->id; points = (jint) a->points;
        title = (*env)->NewStringUTF(env, a->title ? a->title : "");
        if (event->type == RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW ||
            event->type == RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE)
            desc = (*env)->NewStringUTF(env, a->measured_progress);
        else
            desc = (*env)->NewStringUTF(env, a->description ? a->description : "");
        char url[512]; url[0] = 0;
        rc_client_achievement_get_image_url(a, RC_CLIENT_ACHIEVEMENT_STATE_UNLOCKED, url, sizeof(url));
        badge = (*env)->NewStringUTF(env, url);
    } else if (event->server_error) {
        desc = (*env)->NewStringUTF(env, event->server_error->error_message ? event->server_error->error_message : "");
    } else if (event->subset) {
        const rc_client_subset_t *s = event->subset;
        id = (jint) s->id;
        title = (*env)->NewStringUTF(env, s->title ? s->title : "");
        if (s->badge_url && s->badge_url[0]) badge = (*env)->NewStringUTF(env, s->badge_url);
    }
    (*env)->CallStaticVoidMethod(env, g_cls, mid_onEvent, type, id, points, title, desc, badge);
    if (title) (*env)->DeleteLocalRef(env, title);
    if (desc) (*env)->DeleteLocalRef(env, desc);
    if (badge) (*env)->DeleteLocalRef(env, badge);
}

static void rc_login_cb(int result, const char *error_message, rc_client_t *client, void *userdata) {
    (void) client; (void) userdata;
    JNIEnv *env = ra_env();
    if (!env || !g_cls) return;
    jstring err = error_message ? (*env)->NewStringUTF(env, error_message) : NULL;
    (*env)->CallStaticVoidMethod(env, g_cls, mid_onLogin, (jint) result, err);
    if (err) (*env)->DeleteLocalRef(env, err);
}

static void rc_load_cb(int result, const char *error_message, rc_client_t *client, void *userdata) {
    (void) client; (void) userdata;
    JNIEnv *env = ra_env();
    if (!env || !g_cls) return;
    jstring err = error_message ? (*env)->NewStringUTF(env, error_message) : NULL;
    (*env)->CallStaticVoidMethod(env, g_cls, mid_onLoad, (jint) result, err);
    if (err) (*env)->DeleteLocalRef(env, err);
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_ra_RaNative_raInit(JNIEnv *env, jobject thiz) {
    if (g_client) return JNI_TRUE;
    jclass clazz = (*env)->GetObjectClass(env, thiz);
    mid_serverCall = (*env)->GetStaticMethodID(env, clazz, "doServerCall", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    mid_onLogin = (*env)->GetStaticMethodID(env, clazz, "onLoginResult", "(ILjava/lang/String;)V");
    mid_onLoad = (*env)->GetStaticMethodID(env, clazz, "onLoadResult", "(ILjava/lang/String;)V");
    mid_onEvent = (*env)->GetStaticMethodID(env, clazz, "onEvent", "(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    mid_onLbEvent = (*env)->GetStaticMethodID(env, clazz, "onLbEvent", "(IIIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    mid_onLbEntries = (*env)->GetStaticMethodID(env, clazz, "onLbEntries", "(IIII[Ljava/lang/String;Ljava/lang/String;)V");
    if (!mid_serverCall || !mid_onLogin || !mid_onLoad || !mid_onEvent || !mid_onLbEvent || !mid_onLbEntries) {
        LOGE("raInit: missing method ids"); return JNI_FALSE;
    }
    g_client = rc_client_create(rc_read, rc_server_call);
    if (!g_client) { LOGE("raInit: rc_client_create failed"); return JNI_FALSE; }
    g_cls = (*env)->NewGlobalRef(env, clazz);
    rc_client_set_event_handler(g_client, rc_event);
    rc_client_set_hardcore_enabled(g_client, 0);
    LOGI("raInit: rc_client created (softcore)");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raLoginPassword(JNIEnv *env, jclass c, jstring user, jstring pass) {
    (void) c;
    if (!g_client) return;
    const char *u = (*env)->GetStringUTFChars(env, user, NULL);
    const char *p = (*env)->GetStringUTFChars(env, pass, NULL);
    rc_client_begin_login_with_password(g_client, u, p, rc_login_cb, NULL);
    (*env)->ReleaseStringUTFChars(env, user, u);
    (*env)->ReleaseStringUTFChars(env, pass, p);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raLoginToken(JNIEnv *env, jclass c, jstring user, jstring token) {
    (void) c;
    if (!g_client) return;
    const char *u = (*env)->GetStringUTFChars(env, user, NULL);
    const char *t = (*env)->GetStringUTFChars(env, token, NULL);
    rc_client_begin_login_with_token(g_client, u, t, rc_login_cb, NULL);
    (*env)->ReleaseStringUTFChars(env, user, u);
    (*env)->ReleaseStringUTFChars(env, token, t);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raServerResponse(JNIEnv *env, jclass c, jlong handle, jint status, jbyteArray body) {
    (void) c;
    ra_call_t *h = (ra_call_t *) (uintptr_t) handle;
    if (!h) return;
    rc_api_server_response_t resp; memset(&resp, 0, sizeof(resp));
    jbyte *buf = NULL; jsize len = 0;
    if (body) { len = (*env)->GetArrayLength(env, body); buf = (*env)->GetByteArrayElements(env, body, NULL); }
    resp.body = (const char *) buf;
    resp.body_length = (size_t) len;
    resp.http_status_code = (int) status;
    if (h->cb) h->cb(&resp, h->data);
    if (body) (*env)->ReleaseByteArrayElements(env, body, buf, JNI_ABORT);
    free(h);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raLoadGame(JNIEnv *env, jclass c, jint consoleId, jstring path) {
    (void) c;
    if (!g_client) return;
    const char *fp = (*env)->GetStringUTFChars(env, path, NULL);
    rc_client_begin_identify_and_load_game(g_client, (uint32_t) consoleId, fp, NULL, 0, rc_load_cb, NULL);
    (*env)->ReleaseStringUTFChars(env, path, fp);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raLoadGameFd(JNIEnv *env, jclass c, jint consoleId, jint fd) {
    (void) env; (void) c;
    if (!g_client) return;
    g_hash_fd = (int) fd;
    rc_hash_init_custom_filereader(&g_ra_filereader);
    LOGI("raLoadGameFd: hashing fd=%d", (int) fd);
    rc_client_begin_identify_and_load_game(g_client, (uint32_t) consoleId, "ra:fd", NULL, 0, rc_load_cb, NULL);
    rc_hash_init_custom_filereader(NULL);
    g_hash_fd = -1;
}

JNIEXPORT void JNICALL Java_com_seedlessds_app_ra_RaNative_raUnloadGame(JNIEnv *e, jclass c) {
    (void) e; (void) c; if (g_client) rc_client_unload_game(g_client);
}
JNIEXPORT void JNICALL Java_com_seedlessds_app_ra_RaNative_raDoFrame(JNIEnv *e, jclass c) {
    (void) e; (void) c; if (g_client) rc_client_do_frame(g_client);
}
JNIEXPORT void JNICALL Java_com_seedlessds_app_ra_RaNative_raReset(JNIEnv *e, jclass c) {
    (void) e; (void) c; if (g_client) rc_client_reset(g_client);
}
JNIEXPORT void JNICALL Java_com_seedlessds_app_ra_RaNative_raIdle(JNIEnv *e, jclass c) {
    (void) e; (void) c; if (g_client) rc_client_idle(g_client);
}
JNIEXPORT void JNICALL Java_com_seedlessds_app_ra_RaNative_raLogout(JNIEnv *e, jclass c) {
    (void) e; (void) c; if (g_client) rc_client_logout(g_client);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raSetToggles(JNIEnv *e, jclass c, jboolean spectator, jboolean unofficial, jboolean encore) {
    (void) e; (void) c;
    if (!g_client) return;
    rc_client_set_spectator_mode_enabled(g_client, spectator ? 1 : 0);
    rc_client_set_unofficial_enabled(g_client, unofficial ? 1 : 0);
    rc_client_set_encore_mode_enabled(g_client, encore ? 1 : 0);
}

JNIEXPORT jstring JNICALL Java_com_seedlessds_app_ra_RaNative_raGetToken(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    const rc_client_user_t *u = rc_client_get_user_info(g_client);
    return (u && u->token) ? (*env)->NewStringUTF(env, u->token) : NULL;
}
JNIEXPORT jstring JNICALL Java_com_seedlessds_app_ra_RaNative_raGetUsername(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    const rc_client_user_t *u = rc_client_get_user_info(g_client);
    return (u && u->display_name) ? (*env)->NewStringUTF(env, u->display_name) : NULL;
}
JNIEXPORT jint JNICALL Java_com_seedlessds_app_ra_RaNative_raGetScore(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    if (!g_client) return 0;
    const rc_client_user_t *u = rc_client_get_user_info(g_client);
    return u ? (jint) u->score_softcore : 0;
}

JNIEXPORT jint JNICALL Java_com_seedlessds_app_ra_RaNative_raGetHardcoreScore(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    if (!g_client) return 0;
    const rc_client_user_t *u = rc_client_get_user_info(g_client);
    return u ? (jint) u->score : 0;
}

JNIEXPORT jstring JNICALL Java_com_seedlessds_app_ra_RaNative_raRichPresence(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    char buf[256];
    size_t n = rc_client_get_rich_presence_message(g_client, buf, sizeof(buf));
    return n ? (*env)->NewStringUTF(env, buf) : NULL;
}

JNIEXPORT jstring JNICALL Java_com_seedlessds_app_ra_RaNative_raUserAgentClause(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    char buf[128];
    rc_client_get_user_agent_clause(g_client, buf, sizeof(buf));
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL Java_com_seedlessds_app_ra_RaNative_raGameSummary(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    const rc_client_game_t *g = rc_client_get_game_info(g_client);
    if (!g || g->id == 0) return NULL;
    rc_client_user_game_summary_t s;
    memset(&s, 0, sizeof(s));
    rc_client_get_user_game_summary(g_client, &s);
    char icon[256]; icon[0] = 0;
    rc_client_game_get_image_url(g, icon, sizeof(icon));
    char buf[512];
    snprintf(buf, sizeof(buf), "%s\x1f%u\x1f%u\x1f%u\x1f%s", g->title ? g->title : "?", g->id,
             s.num_core_achievements, s.num_unlocked_achievements, icon);
    return (*env)->NewStringUTF(env, buf);
}

JNIEXPORT jstring JNICALL Java_com_seedlessds_app_ra_RaNative_raUserAvatar(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    const rc_client_user_t *u = rc_client_get_user_info(g_client);
    if (!u) return NULL;
    char buf[256]; buf[0] = 0;
    rc_client_user_get_image_url(u, buf, sizeof(buf));
    return buf[0] ? (*env)->NewStringUTF(env, buf) : NULL;
}

#define FS "\x1f"
JNIEXPORT jobjectArray JNICALL
Java_com_seedlessds_app_ra_RaNative_raAchievementList(JNIEnv *env, jclass c, jint category, jint grouping) {
    (void) c;
    if (!g_client) return NULL;
    rc_client_achievement_list_t *list =
        rc_client_create_achievement_list(g_client, (int) category, (int) grouping);
    if (!list) return NULL;
    uint32_t total = 0;
    for (uint32_t b = 0; b < list->num_buckets; b++) total += list->buckets[b].num_achievements;
    jclass strCls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, (jsize) total, strCls, NULL);
    jsize idx = 0;
    char buf[2048]; char url[512];
    for (uint32_t b = 0; b < list->num_buckets; b++) {
        const rc_client_achievement_bucket_t *bk = &list->buckets[b];
        for (uint32_t i = 0; i < bk->num_achievements; i++) {
            const rc_client_achievement_t *a = bk->achievements[i];
            url[0] = 0;
            rc_client_achievement_get_image_url(a, a->state, url, sizeof(url));
            snprintf(buf, sizeof(buf),
                     "%u" FS "%s" FS "%u" FS "%s" FS "%s" FS "%u" FS "%u" FS "%s" FS "%d" FS "%s",
                     (unsigned) bk->bucket_type, bk->label ? bk->label : "",
                     (unsigned) a->id, a->title ? a->title : "", a->description ? a->description : "",
                     (unsigned) a->points, (unsigned) a->state,
                     a->measured_progress, (int) (a->measured_percent * 100.0f + 0.5f), url);
            jstring s = (*env)->NewStringUTF(env, buf);
            (*env)->SetObjectArrayElement(env, arr, idx++, s);
            (*env)->DeleteLocalRef(env, s);
        }
    }
    rc_client_destroy_achievement_list(list);
    (*env)->DeleteLocalRef(env, strCls);
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_ra_RaNative_raHasLeaderboards(JNIEnv *env, jclass c) {
    (void) env; (void) c;
    return (g_client && rc_client_has_leaderboards(g_client)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_seedlessds_app_ra_RaNative_raLeaderboardList(JNIEnv *env, jclass c, jint grouping) {
    (void) c;
    if (!g_client) return NULL;
    rc_client_leaderboard_list_t *list = rc_client_create_leaderboard_list(g_client, (int) grouping);
    if (!list) return NULL;
    uint32_t total = 0;
    for (uint32_t b = 0; b < list->num_buckets; b++) total += list->buckets[b].num_leaderboards;
    jclass strCls = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arr = (*env)->NewObjectArray(env, (jsize) total, strCls, NULL);
    jsize idx = 0;
    char buf[1024];
    for (uint32_t b = 0; b < list->num_buckets; b++) {
        const rc_client_leaderboard_bucket_t *bk = &list->buckets[b];
        for (uint32_t i = 0; i < bk->num_leaderboards; i++) {
            const rc_client_leaderboard_t *lb = bk->leaderboards[i];
            snprintf(buf, sizeof(buf), "%u" FS "%s" FS "%u" FS "%s" FS "%s" FS "%u" FS "%s",
                     (unsigned) bk->bucket_type, bk->label ? bk->label : "",
                     (unsigned) lb->id, lb->title ? lb->title : "",
                     lb->description ? lb->description : "", (unsigned) lb->state,
                     lb->tracker_value ? lb->tracker_value : "");
            jstring s = (*env)->NewStringUTF(env, buf);
            (*env)->SetObjectArrayElement(env, arr, idx++, s);
            (*env)->DeleteLocalRef(env, s);
        }
    }
    rc_client_destroy_leaderboard_list(list);
    (*env)->DeleteLocalRef(env, strCls);
    return arr;
}

static void lb_entries_cb(int result, const char *error_message,
                          rc_client_leaderboard_entry_list_t *list, rc_client_t *client, void *ud) {
    (void) client;
    JNIEnv *env = ra_env();
    if (!env || !g_cls) { if (list) rc_client_destroy_leaderboard_entry_list(list); return; }
    jint nonce = (jint) (intptr_t) ud;
    jint userIndex = -1, total = 0;
    jobjectArray arr = NULL;
    if (result == RC_OK && list) {
        userIndex = (jint) list->user_index;
        total = (jint) list->total_entries;
        jclass strCls = (*env)->FindClass(env, "java/lang/String");
        arr = (*env)->NewObjectArray(env, (jsize) list->num_entries, strCls, NULL);
        char buf[800]; char img[512];
        for (uint32_t i = 0; i < list->num_entries; i++) {
            const rc_client_leaderboard_entry_t *e = &list->entries[i];
            img[0] = 0;
            rc_client_leaderboard_entry_get_user_image_url(e, img, sizeof(img));
            snprintf(buf, sizeof(buf), "%u" FS "%s" FS "%s" FS "%u" FS "%s",
                     (unsigned) e->rank, e->user ? e->user : "", e->display,
                     (unsigned) e->index, img);
            jstring s = (*env)->NewStringUTF(env, buf);
            (*env)->SetObjectArrayElement(env, arr, (jsize) i, s);
            (*env)->DeleteLocalRef(env, s);
        }
        (*env)->DeleteLocalRef(env, strCls);
    }
    jstring err = error_message ? (*env)->NewStringUTF(env, error_message) : NULL;
    (*env)->CallStaticVoidMethod(env, g_cls, mid_onLbEntries, nonce, (jint) result, userIndex, total, arr, err);
    if (arr) (*env)->DeleteLocalRef(env, arr);
    if (err) (*env)->DeleteLocalRef(env, err);
    if (list) rc_client_destroy_leaderboard_entry_list(list);
}

JNIEXPORT void JNICALL
Java_com_seedlessds_app_ra_RaNative_raFetchLbEntries(JNIEnv *env, jclass c, jint lbId, jint count, jboolean aroundUser, jint nonce) {
    (void) env; (void) c;
    if (!g_client) return;
    void *ud = (void *) (intptr_t) nonce;
    if (aroundUser)
        rc_client_begin_fetch_leaderboard_entries_around_user(g_client, (uint32_t) lbId, (uint32_t) count, lb_entries_cb, ud);
    else
        rc_client_begin_fetch_leaderboard_entries(g_client, (uint32_t) lbId, 1u, (uint32_t) count, lb_entries_cb, ud);
}

JNIEXPORT jbyteArray JNICALL
Java_com_seedlessds_app_ra_RaNative_raSerializeProgress(JNIEnv *env, jclass c) {
    (void) c;
    if (!g_client) return NULL;
    size_t size = rc_client_progress_size(g_client);
    if (size == 0) return NULL;
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) size);
    if (!arr) return NULL;
    jbyte *buf = (*env)->GetByteArrayElements(env, arr, NULL);
    if (!buf) return NULL;
    int rc = rc_client_serialize_progress_sized(g_client, (uint8_t *) buf, size);
    (*env)->ReleaseByteArrayElements(env, arr, buf, 0);
    if (rc != 0) return NULL;
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_seedlessds_app_ra_RaNative_raDeserializeProgress(JNIEnv *env, jclass c, jbyteArray data) {
    (void) c;
    if (!g_client) return JNI_FALSE;
    if (!data) {
        rc_client_deserialize_progress_sized(g_client, NULL, 0);
        return JNI_TRUE;
    }
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);
    if (!buf) return JNI_FALSE;
    int rc = rc_client_deserialize_progress_sized(g_client, (const uint8_t *) buf, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);
    return (rc == 0) ? JNI_TRUE : JNI_FALSE;
}

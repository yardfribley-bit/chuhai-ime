/* JNI 只传 UTF-8 字节数组，避免 modified UTF-8 对表情字符的破坏。 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

extern void *chuhai_create(const char *directory);
extern char *chuhai_call(void *session, const char *message);
extern void chuhai_free(char *text);
extern void chuhai_destroy(void *session);

static char *copy_bytes(JNIEnv *env, jbyteArray bytes) {
    jsize length = (*env)->GetArrayLength(env, bytes);
    char *text = malloc((size_t)length + 1);
    if (!text) return NULL;
    (*env)->GetByteArrayRegion(env, bytes, 0, length, (jbyte *)text);
    text[length] = 0;
    return text;
}

JNIEXPORT jlong JNICALL Java_org_chuhaijian_ime_NativeBridge_create(JNIEnv *env, jclass type, jbyteArray directory) {
    (void)type;
    char *text = copy_bytes(env, directory);
    if (!text) return 0;
    void *session = chuhai_create(text);
    free(text);
    return (jlong)(intptr_t)session;
}

JNIEXPORT jbyteArray JNICALL Java_org_chuhaijian_ime_NativeBridge_callBytes(JNIEnv *env, jclass type, jlong handle, jbyteArray message) {
    (void)type;
    char *text = copy_bytes(env, message);
    if (!text) return NULL;
    char *result = chuhai_call((void *)(intptr_t)handle, text);
    free(text);
    jsize length = (jsize)strlen(result);
    jbyteArray bytes = (*env)->NewByteArray(env, length);
    if (bytes) (*env)->SetByteArrayRegion(env, bytes, 0, length, (jbyte *)result);
    chuhai_free(result);
    return bytes;
}

JNIEXPORT void JNICALL Java_org_chuhaijian_ime_NativeBridge_destroy(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    chuhai_destroy((void *)(intptr_t)handle);
}

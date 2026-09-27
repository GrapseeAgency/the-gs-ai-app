/* gs_abi.h — shared status codes and FFI rules for every GS AI C ABI surface.
 *
 * FFI RULES (dossier rule 4, enforced not documented):
 *   - No C++ exceptions cross this boundary. Every entry catches internally
 *     and returns a gs_status_t.
 *   - Strings passed IN are borrowed. The callee never frees them.
 *   - Strings returned OUT are owned by the caller and must be released with
 *     the matching gs_free_* function.
 *   - Opaque handles are created by *_create and destroyed by *_free only.
 *
 * Compiles standalone:  g++ -c -std=c++17 -I include gs_abi.h
 */
#ifndef GS_ABI_H
#define GS_ABI_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- status codes -------------------------------------------------------
 * GS_OK is 0 so callers can treat a negative return as failure without
 * needing the enum. Every non-OK value is negative.
 */
typedef enum gs_status {
    GS_OK                 = 0,
    GS_ERR_INVALID_ARG    = -1,
    GS_ERR_NO_MEMORY      = -2,
    GS_ERR_IO             = -3,   /* file / device */
    GS_ERR_UNAVAILABLE    = -4,   /* backend absent or not loadable */
    GS_ERR_GENERATION     = -5,   /* decode failed or produced nothing */
    GS_ERR_TIMEOUT        = -6,
    GS_ERR_INTERNAL       = -7,   /* caught C++ exception, msg in gs_last_error */
    GS_ERR_UNSUPPORTED    = -8,   /* feature deliberately not implemented */
    GS_ERR_NOT_FOUND      = -9
} gs_status_t;

/* Last error text for the calling thread. Never freed by the caller; owned by
 * the library. Returns "" when no error is pending. */
const char* gs_last_error(void);
void gs_clear_error(void);

/* Record an error for the calling thread. Added because the three wrappers
 * each kept a PRIVATE thread_local: gs_abi's g_last_error, llama_wrapper's
 * t_err, and the mobile one. gs_last_error() read only the first, so 43 writes
 * in the llama wrapper and every mobile error were unreachable -- a caller got
 * "" for a failure that had a perfectly good explanation sitting in a variable
 * nothing read. One channel, one accessor. */
void gs_set_error(const char* msg);

/* Generic owned-string release. Every gs_free_* in other headers forwards here
 * so callers only need one free convention. */
void gs_free_string(char* s);

/* Device capability class, mirroring gs-core's device gate. */
typedef enum gs_device_class {
    GS_DEVICE_UNKNOWN = 0,
    GS_DEVICE_LOW     = 1,   /* local inference disabled entirely */
    GS_DEVICE_MID     = 2,   /* local inference <= 1B params */
    GS_DEVICE_HIGH    = 3    /* local inference 1-3B params */
} gs_device_class_t;

/* Cached device class detection. Safe to call repeatedly. */
gs_device_class_t gs_device_class(void);
/* 0 when local inference is permitted for max_params, negative + reason if not. */
int32_t gs_device_allows(uint64_t max_params);
const char* gs_device_reason(void);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* GS_ABI_H */

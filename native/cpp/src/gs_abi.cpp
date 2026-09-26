// gs_abi.cpp — shared error state + device gate for the GS AI C ABI.
#include "gs_abi.h"

#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <unistd.h>

namespace {

// One error slot per thread. The C ABI is called from several threads
// (server handlers, plugin hosts) and a single global would let one thread
// read another's failure reason.
thread_local std::string g_last_error;

} // namespace

extern "C" {

const char* gs_last_error(void) { return g_last_error.c_str(); }

void gs_clear_error(void) { g_last_error.clear(); }

void gs_free_string(char* s) {
    // The ABI promises caller-owned strings are malloc'd, so free() is the
    // correct release. Guarding NULL keeps every gs_free_* idempotent.
    if (s) std::free(s);
}

} // extern "C"

// ---------------------------------------------------------------------------
// Device capability gate (dossier Phase 3).
//
// Class is decided once and cached. The rule is asymmetric on purpose: a weak
// device must never be made to carry a model it cannot hold, because the
// failure mode is thermal throttling and a user's battery, not a crash.
// ---------------------------------------------------------------------------
namespace {

struct DeviceInfo {
    gs_device_class_t cls;
    uint64_t ram_bytes;
    int cores;
    bool resolved;
    char reason[256];
};

DeviceInfo detect_device() {
    DeviceInfo d{};
    d.resolved = true;

    // RAM: prefer sysconf, fall back to /proc/meminfo.
    d.ram_bytes = 0;
#if defined(_SC_PHYS_PAGES) && defined(_SC_PAGESIZE)
    long pages = sysconf(_SC_PHYS_PAGES);
    long psize = sysconf(_SC_PAGESIZE);
    if (pages > 0 && psize > 0) d.ram_bytes = (uint64_t)pages * (uint64_t)psize;
#endif
    if (d.ram_bytes == 0) {
        if (FILE* f = std::fopen("/proc/meminfo", "r")) {
            unsigned long long kb = 0;
            if (std::fscanf(f, "MemTotal: %llu kB", &kb) == 1) d.ram_bytes = kb * 1024ull;
            std::fclose(f);
        }
    }

    d.cores = (int)std::thread::hardware_concurrency();
    if (d.cores <= 0) d.cores = 1;

    const uint64_t GB = 1024ull * 1024ull * 1024ull;

    if (d.ram_bytes >= 6 * GB && d.cores >= 6) {
        d.cls = GS_DEVICE_HIGH;
        std::snprintf(d.reason, sizeof(d.reason),
                      "HIGH: %.1f GB RAM, %d cores -> local inference 1-3B",
                      (double)d.ram_bytes / (double)GB, d.cores);
    } else if (d.ram_bytes >= 4 * GB && d.cores >= 4) {
        d.cls = GS_DEVICE_MID;
        std::snprintf(d.reason, sizeof(d.reason),
                      "MID: %.1f GB RAM, %d cores -> local inference <=1B only",
                      (double)d.ram_bytes / (double)GB, d.cores);
    } else {
        d.cls = GS_DEVICE_LOW;
        std::snprintf(d.reason, sizeof(d.reason),
                      "LOW: %.1f GB RAM, %d cores -> local inference DISABLED, "
                      "100%% route to provider pool",
                      (double)d.ram_bytes / (double)GB, d.cores);
    }
    return d;
}

const DeviceInfo& device() {
    static const DeviceInfo d = detect_device();
    return d;
}

} // namespace

extern "C" {

gs_device_class_t gs_device_class(void) { return device().cls; }

const char* gs_device_reason(void) { return device().reason; }

int32_t gs_device_allows(uint64_t max_params) {
    const DeviceInfo& d = device();
    // "params" here is treated as an upper bound in billions. Callers pass a
    // conservative estimate; the gate is about refusing obviously-too-large
    // models, not about exact accounting.
    const uint64_t B = 1'000'000'000ull;

    switch (d.cls) {
        case GS_DEVICE_LOW:
            return -1;  // never load a model
        case GS_DEVICE_MID:
            return (max_params <= 1ull * B) ? 0 : -1;
        case GS_DEVICE_HIGH:
            return (max_params <= 3ull * B) ? 0 : -1;
        default:
            return 0;
    }
}

} // extern "C"

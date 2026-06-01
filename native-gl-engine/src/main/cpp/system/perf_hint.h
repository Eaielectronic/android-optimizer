/**
 * perf_hint.h — Interface performance thread boosting.
 */
#pragma once

namespace androidopt {
    int bindToPCores();
    int boostCurrentThread();
    int getPCoreCount();
}

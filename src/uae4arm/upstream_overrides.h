#pragma once

#include "sysconfig.h"
#include "sysdeps.h"

struct romdata;

bool uae4arm_allows_audio_dma_wait_hack(int cachesize, int m68k_speed, int instruction_count, int dmaofftime_cpu_cnt);
struct romdata* uae4arm_resolve_action_replay_romdata(const TCHAR* cartfile);
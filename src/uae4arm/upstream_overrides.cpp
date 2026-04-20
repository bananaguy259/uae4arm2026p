#include "sysconfig.h"
#include "sysdeps.h"

#include "rommgr.h"

#include "upstream_overrides.h"

bool uae4arm_allows_audio_dma_wait_hack(const int cachesize, const int m68k_speed, const int instruction_count, const int dmaofftime_cpu_cnt)
{
#ifdef AMIBERRY
	return cachesize || m68k_speed == -1 || (instruction_count - dmaofftime_cpu_cnt) >= 60;
#else
	return cachesize || (instruction_count - dmaofftime_cpu_cnt) >= 60;
#endif
}

struct romdata* uae4arm_resolve_action_replay_romdata(const TCHAR* cartfile)
{
#ifdef AMIBERRY
	if (!_tcscmp(cartfile, _T(":HRTMon"))) {
		return getromdatabyid(63);
	}
#endif

	return getromdatabypath(cartfile);
}
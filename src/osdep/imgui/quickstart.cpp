#include "sysdeps.h"
#include "blkdev.h"
#include "imgui.h"
#include "imgui_internal.h"
#include "options.h"
#include "gui/gui_handling.h"
#include "disk.h"
#include "imgui_panels.h"
#include "uae.h"

std::vector<const char *> qs_models;
std::vector<const char *> qs_configs;

// Cache write-protect state per drive to avoid re-opening (and unpacking)
// archive files on every frame.
static struct {
	std::string path;
	bool wp = false;
} qs_wp_cache[4];

static bool qs_cached_disk_getwriteprotect(struct uae_prefs *p, const TCHAR *name, int num)
{
	if (num < 0 || num >= 4)
		return false;
	std::string current(name ? name : "");
	if (qs_wp_cache[num].path != current) {
		qs_wp_cache[num].path = current;
		qs_wp_cache[num].wp = current.empty() ? false : (disk_getwriteprotect(p, name, num) != 0);
	}
	return qs_wp_cache[num].wp;
}

static void qs_invalidate_wp_cache(int num)
{
	if (num >= 0 && num < 4)
		qs_wp_cache[num].path.clear();
}

// Quickstart MRU display helpers/state
static bool qs_ignore_list_change = false;
static std::vector<std::string> qs_disk_display;
static std::vector<std::string> qs_cd_display;
static std::vector<std::string> qs_whd_display;

static void qs_refresh_disk_list_model() {
    qs_disk_display.clear();
    for (const auto &entry: lstMRUDiskList) {
        const std::string full_path = entry;
        const auto sep = full_path.find_last_of("/\\");
        std::string filename = sep == std::string::npos ? full_path : full_path.substr(sep + 1);
        qs_disk_display.emplace_back(filename.append(" { ").append(full_path).append(" }"));
    }
}

static void qs_refresh_cd_list_model() {
    qs_cd_display.clear();
    const auto cd_drives = get_cd_drives();
    for (const auto &drive: cd_drives)
        qs_cd_display.emplace_back(drive);
    for (const auto &entry: lstMRUCDList) {
        const std::string full_path = entry;
        const auto sep = full_path.find_last_of("/\\");
        std::string filename = sep == std::string::npos ? full_path : full_path.substr(sep + 1);
        qs_cd_display.emplace_back(filename.append(" { ").append(full_path).append(" }"));
    }
}

static void qs_refresh_whd_list_model() {
    qs_whd_display.clear();
    for (const auto &entry: lstMRUWhdloadList) {
        const std::string full_path = entry;
        const auto sep = full_path.find_last_of("/\\");
        std::string filename = sep == std::string::npos ? full_path : full_path.substr(sep + 1);
        qs_whd_display.emplace_back(filename.append(" { ").append(full_path).append(" }"));
    }
}

static int qs_find_in_mru(const std::vector<std::string> &mru, const char *path) {
    if (!path || !*path)
        return -1;
    for (int i = 0; i < static_cast<int>(mru.size()); ++i) {
        if (mru[i] == path)
            return i;
    }
    return -1;
}

static void qs_set_control_state(int model, bool &df1_visible, bool &cd_visible, bool &df0_editable) {
    df1_visible = true;
    cd_visible = false;
    df0_editable = true;

    switch (model) {
        case 8: // CD32
        case 9: // CDTV
        case 10: // American Laser Games / Picmatic
        case 11: // Arcadia Multi Select system
            df0_editable = true;
            df1_visible = false;
            cd_visible = true;
            break;
        default:
            break;
    }
}

void Quickstart_ApplyDefaults() {
    built_in_prefs(&changed_prefs, quickstart_model, quickstart_conf, 0, 0);

    // Enforce constraints similar to WinUAE
    if (quickstart_model <= 4) { // A500, A500+, A600, A1000, A1200
        // Force DF0 to DD if it was set to HD
        if (changed_prefs.floppyslots[0].dfxtype == DRV_35_HD)
            changed_prefs.floppyslots[0].dfxtype = DRV_35_DD;
        
        // Disable other drives for these stock models if needed, 
        // but built_in_prefs usually handles it. 
        // We just ensure DF0 isn't HD.
    }

    switch (quickstart_model) {
        case 0: // A500
        case 1: // A500+
        case 2: // A600
        case 3: // A1000
        case 4: // A1200
        case 5: // A3000
            // df0 always active
            // Ensure type is DD for A500-A1200 (Cases 0-4) handled above, 
            // A3000 (Case 5) can have HD, so we don't force it to DD here.
            if (changed_prefs.floppyslots[0].dfxtype == DRV_NONE)
                changed_prefs.floppyslots[0].dfxtype = DRV_35_DD;
            
            changed_prefs.floppyslots[1].dfxtype = DRV_NONE;

            // No CD available
            changed_prefs.cdslots[0].inuse = false;
            changed_prefs.cdslots[0].type = SCSI_UNIT_DISABLED;

            // Set joystick port to Default
            changed_prefs.jports[1].mode = 0;
            break;
        case 6: // A4000
        case 7: // A4000T
        case 12: // Macrosystem
            // df0 always active
            if (changed_prefs.floppyslots[0].dfxtype == DRV_NONE)
                changed_prefs.floppyslots[0].dfxtype = DRV_35_HD;
            changed_prefs.floppyslots[1].dfxtype = DRV_NONE;

            // No CD available
            changed_prefs.cdslots[0].inuse = false;
            changed_prefs.cdslots[0].type = SCSI_UNIT_DISABLED;

            // Set joystick port to Default
            changed_prefs.jports[1].mode = 0;
            break;

        case 8: // CD32
        case 9: // CDTV
        case 10: // American Laser Games / Picmatic
        case 11: // Arcadia Multi Select system
            // No floppy drive available, CD available
            changed_prefs.floppyslots[0].dfxtype = DRV_NONE;
            changed_prefs.floppyslots[1].dfxtype = DRV_NONE;
            changed_prefs.cdslots[0].inuse = true;
            changed_prefs.cdslots[0].type = SCSI_UNIT_DEFAULT;
            changed_prefs.gfx_monitor[0].gfx_size.width = 720;
            changed_prefs.gfx_monitor[0].gfx_size.height = 568;
            // Set joystick port to CD32 mode
            changed_prefs.jports[1].mode = 7;
            break;
        default:
            break;
    }
}

void render_panel_quickstart() {
    // Check if we need to apply Quickstart defaults on first show
    static bool initial_sync_done = false;
    if (!initial_sync_done && !emulating && !last_loaded_config[0]) {
        Quickstart_ApplyDefaults();
        initial_sync_done = true;
    }

    // Refresh MRU display lists once per frame
    qs_refresh_disk_list_model();
    qs_refresh_cd_list_model();
    qs_refresh_whd_list_model();

    // State for asynchronous file dialogs in this panel
    static int qs_pending_floppy_drive = -1;
    static bool qs_pending_cd = false;
    static bool qs_pending_whd = false;

    bool df1_visible = true;
    bool df1_editable = true;
    bool df2_visible = false;
    bool df3_visible = false;
    bool cd_visible = false;
    bool df0_editable = true;
    qs_set_control_state(quickstart_model, df1_visible, cd_visible, df0_editable);
    df1_editable = df1_visible;
    df2_visible = changed_prefs.floppyslots[2].dfxtype != DRV_NONE;
    df3_visible = changed_prefs.floppyslots[3].dfxtype != DRV_NONE;

    ImGui::Indent(4.0f);

    BeginGroupBox("Emulated Hardware");
    if (ImGui::BeginTable("QuickstartModelTable", 2, ImGuiTableFlags_SizingStretchProp,
                          ImVec2(ImGui::GetContentRegionAvail().x - 15.0f, 0.0f))) {
        ImGui::TableSetupColumn("Label", ImGuiTableColumnFlags_WidthFixed, BUTTON_WIDTH * 1.33f);
        ImGui::TableSetupColumn("Control", ImGuiTableColumnFlags_WidthStretch);

        // Model row
        ImGui::TableNextRow();
        ImGui::TableSetColumnIndex(0);
        ImGui::AlignTextToFramePadding();
        ImGui::TextUnformatted("Model:");
        ImGui::TableSetColumnIndex(1);

        if (ImGui::BeginCombo("##ModelCombo", qs_models[quickstart_model])) {
            for (int i = 0; i < static_cast<int>(qs_models.size()); i++) {
                const bool is_selected = (quickstart_model == i);
                if (is_selected)
                    ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
                ImGui::PushID(i);
                if (ImGui::Selectable(qs_models[i], is_selected)) {
                    quickstart_model = i;
                    qs_configs.clear();
                    for (auto &config: amodels[quickstart_model].configs) {
                        if (config[0] == '\0')
                            break;
                        qs_configs.push_back(config);
                    }
                    quickstart_conf = 0;
                    Quickstart_ApplyDefaults();
                }
                ImGui::PopID();
                if (is_selected) {
                    ImGui::PopStyleColor();
                    ImGui::SetItemDefaultFocus();
                }
            }
            ImGui::EndCombo();
        }
        // Bevel around the combo frame
        AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());

        ImGui::SameLine();
        bool ntsc = changed_prefs.ntscmode != 0;
        if (AmigaCheckbox("NTSC", &ntsc)) {
            changed_prefs.ntscmode = ntsc;
            changed_prefs.chipset_refreshrate = ntsc ? 60 : 50;
        }
        ShowHelpMarker("Use NTSC video mode (60Hz) instead of PAL (50Hz)");

        // Configuration row
        ImGui::TableNextRow();
        ImGui::TableSetColumnIndex(0);
        ImGui::AlignTextToFramePadding();
        ImGui::TextUnformatted("Configuration:");
        ImGui::TableSetColumnIndex(1);

        if (!qs_configs.empty()) {
            if (quickstart_conf < 0 || quickstart_conf >= static_cast<int>(qs_configs.size()))
                quickstart_conf = 0;
            if (ImGui::BeginCombo("##ConfigurationCombo", qs_configs[quickstart_conf])) {
                for (int i = 0; i < static_cast<int>(qs_configs.size()); i++) {
                    const bool is_selected = (quickstart_conf == i);
                    if (is_selected)
                        ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
                    ImGui::PushID(i);
                    if (ImGui::Selectable(qs_configs[i], is_selected)) {
                        quickstart_conf = i;
                        Quickstart_ApplyDefaults();
                    }
                    ImGui::PopID();
                    if (is_selected) {
                        ImGui::PopStyleColor();
                        ImGui::SetItemDefaultFocus();
                    }
                }
                ImGui::EndCombo();
            }
            // Bevel around the combo frame
            AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());
        }
        ImGui::EndTable();
    }
    EndGroupBox("Emulated Hardware");

    ImGui::Spacing();

    BeginGroupBox("Emulated Drives");
    auto render_floppy_drive = [&](int i, bool is_editable) {
        ImGui::PushID(i);
        char label[64];
        snprintf(label, sizeof(label), "DF%d:", i);

        bool drive_enabled = changed_prefs.floppyslots[i].dfxtype != DRV_NONE;
        bool disk_present = std::strlen(changed_prefs.floppyslots[i].df) > 0;

        // 1. Checkbox DFx:
        if (!is_editable) ImGui::BeginDisabled();
        if (AmigaCheckbox(label, &drive_enabled)) {
            if (drive_enabled) {
                changed_prefs.floppyslots[i].dfxtype = DRV_35_DD;
            } else {
                changed_prefs.floppyslots[i].dfxtype = DRV_NONE;
                changed_prefs.floppyslots[i].df[0] = 0;
            }
        }
        if (!is_editable) ImGui::EndDisabled();

        ImGui::SameLine();

        // 2. Select file Button
        const float button_width = BUTTON_WIDTH * 1.33f; // Wider button for "Select file"
        if (!drive_enabled) ImGui::BeginDisabled();
        if (AmigaButton(ICON_FA_FOLDER_OPEN " Select file", ImVec2(button_width, 0))) {
            std::string tmp;
            if (disk_present)
                tmp = changed_prefs.floppyslots[i].df;
            else if (!last_floppy_dir.empty())
                tmp = last_floppy_dir;
            else
                tmp = get_floppy_path();
            OpenFileDialogKey("QUICKSTART", "Select disk image file",
                           "All Supported Files (*.adf,*.adz,*.dms,*.ipf,*.zip,*.7z,*.lha,*.lzh,*.lzx,*.fdi,*.scp,*.wrp,*.dsq,*.gz,*.xz,*.hdf,*.img){.adf,.adz,.dms,.ipf,.zip,.7z,.lha,.lzh,.lzx,.fdi,.scp,.wrp,.dsq,.gz,.xz,.hdf,.img},All Files (*){.*}",
                           tmp);
            qs_pending_floppy_drive = i;
        }
        if (!drive_enabled) ImGui::EndDisabled();

        ImGui::SameLine();

        // 3. Drive Type Combo
        int nn = fromdfxtype(i, changed_prefs.floppyslots[i].dfxtype, changed_prefs.floppyslots[i].dfxsubtype);
        int selectedFloppyType = nn + 1;
        ImGui::SetNextItemWidth(BUTTON_WIDTH);
        snprintf(label, sizeof(label), "##QSFloppyType%d", i);
        if (!drive_enabled) ImGui::BeginDisabled();
        if (ImGui::BeginCombo(label, floppy_drive_types[selectedFloppyType])) {
            for (int n = 0; n < IM_ARRAYSIZE(floppy_drive_types); n++) {
                const bool is_selected = (selectedFloppyType == n);
                if (is_selected)
                    ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
                // Filter out HD options for models that don't support it in Quickstart
                bool allowed = true;
                if (quickstart_model <= 4) { // A500-A1200
                     int sub = 0;
                     int dfxtype = todfxtype(i, n - 1, &sub);
                     if (dfxtype == DRV_35_HD)
                         allowed = false;
                }

                if (allowed) {
                    if (ImGui::Selectable(floppy_drive_types[n], is_selected)) {
                        selectedFloppyType = n;
                        int sub = 0;
                        int dfxtype = todfxtype(i, selectedFloppyType - 1, &sub);
                        changed_prefs.floppyslots[i].dfxtype = dfxtype;
                        changed_prefs.floppyslots[i].dfxsubtype = sub;
    
                        if (dfxtype == DRV_FB) {
                            TCHAR tmp[32];
                            _sntprintf(tmp, sizeof tmp, _T("%d:%s"), selectedFloppyType - 5,
                                       drivebridgeModes[selectedFloppyType - 6].data());
                            _tcscpy(changed_prefs.floppyslots[i].dfxsubtypeid, tmp);
                        } else {
                            changed_prefs.floppyslots[i].dfxsubtypeid[0] = 0;
                        }
                    }
                    if (is_selected) {
                        ImGui::PopStyleColor();
                        ImGui::SetItemDefaultFocus();
                    }
                }
            }
            ImGui::EndCombo();
        }
        // Bevel around the combo frame
        AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());
        if (!drive_enabled) ImGui::EndDisabled();

        ImGui::SameLine();

        // 4. Write-protected
        bool wp_enabled = drive_enabled && !changed_prefs.floppy_read_only && disk_present;
        if (!wp_enabled) ImGui::BeginDisabled();
        bool wp = qs_cached_disk_getwriteprotect(&changed_prefs, changed_prefs.floppyslots[i].df, i);
        snprintf(label, sizeof(label), "WP##QSFloppyWriteProtected%d", i);
        if (AmigaCheckbox(label, &wp)) {
            disk_setwriteprotect(&changed_prefs, i, changed_prefs.floppyslots[i].df, wp);
            qs_invalidate_wp_cache(i);
            if (qs_cached_disk_getwriteprotect(&changed_prefs, changed_prefs.floppyslots[i].df, i) != wp) {
                wp = !wp;
                ShowMessageBox("Set/Clear write protect",
                               "Failed to change write permission.\nMaybe underlying filesystem doesn't support this.");
            }
            DISK_reinsert(i);
        }
        if (!wp_enabled) ImGui::EndDisabled();

        ImGui::SameLine();

        // 5. ? Button
        bool info_enabled = drive_enabled && disk_present;
        if (!info_enabled) ImGui::BeginDisabled();
        snprintf(label, sizeof(label), "?##QSFloppyInfo%d", i);
        if (AmigaButton(label, ImVec2(SMALL_BUTTON_WIDTH, 0))) {
            DisplayDiskInfo(i);
        }
        if (!info_enabled) ImGui::EndDisabled();

        ImGui::SameLine();

        // 6. Eject Button
        bool eject_enabled = drive_enabled && disk_present;
        if (!eject_enabled) ImGui::BeginDisabled();
        snprintf(label, sizeof(label), ICON_FA_EJECT "##QSFloppyEject%d", i);
        if (AmigaButton(label, ImVec2(SMALL_BUTTON_WIDTH, 0))) {
            disk_eject(i);
            changed_prefs.floppyslots[i].df[0] = 0;
            qs_invalidate_wp_cache(i);
        }
        ShowHelpMarker("Eject disk");
        if (!eject_enabled) ImGui::EndDisabled();

        ImGui::SameLine();

        // 7. File Path Combo (inline, compact)
        int selected_index = -1;
        if (disk_present)
            selected_index = qs_find_in_mru(lstMRUDiskList, changed_prefs.floppyslots[i].df);

        std::vector<const char *> items;
        items.push_back("<empty>");
        items.reserve(qs_disk_display.size() + 1);
        for (auto &s: qs_disk_display)
            items.push_back(s.c_str());

        int combo_index = selected_index + 1;
        snprintf(label, sizeof(label), "##QSFloppyImagePath%d", i);
        ImGui::PushItemWidth(-ImGui::GetStyle().ItemSpacing.x * 2.0f);
        if (ImGui::BeginCombo(label, items[combo_index])) {
            for (int n = 0; n < static_cast<int>(items.size()); n++) {
                const bool is_selected = (combo_index == n);
                if (is_selected)
                    ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
                if (ImGui::Selectable(items[n], is_selected)) {
                    combo_index = n;
                    if (combo_index == 0) {
                        disk_eject(i);
                        changed_prefs.floppyslots[i].df[0] = 0;
                        qs_invalidate_wp_cache(i);
                    } else if (!qs_ignore_list_change && combo_index > 0 && combo_index <= static_cast<int>(
                                   lstMRUDiskList.size())) {
                        std::string element = get_full_path_from_disk_list(qs_disk_display[combo_index - 1]);
                        if (element != changed_prefs.floppyslots[i].df) {
                            std::strncpy(changed_prefs.floppyslots[i].df, element.c_str(), MAX_DPATH);
                            DISK_history_add(changed_prefs.floppyslots[i].df, -1, HISTORY_FLOPPY, 0);
                            disk_insert(i, changed_prefs.floppyslots[i].df);
                            qs_invalidate_wp_cache(i);
                            if (!last_loaded_config[0])
                                set_last_active_config(element.c_str());
                        }
                    }
                }
                if (is_selected) {
                    ImGui::PopStyleColor();
                    ImGui::SetItemDefaultFocus();
                }
            }
            ImGui::EndCombo();
        }
        // Bevel around the combo frame
        AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());

        ImGui::PopItemWidth();
        ImGui::PopID();
    };

    // === Drive icon strip ===
    // Tracks which drive slot is currently expanded: 0-3 = floppy, 4 = CD, -1 = none
    static int qs_expanded_drive = 0;

    // Clamp: if the expanded slot is no longer visible, fall back to DF0
    if (qs_expanded_drive == 1 && !df1_visible) qs_expanded_drive = 0;
    if (qs_expanded_drive == 2 && !df2_visible) qs_expanded_drive = 0;
    if (qs_expanded_drive == 3 && !df3_visible) qs_expanded_drive = 0;
    if (qs_expanded_drive == 4 && !cd_visible)  qs_expanded_drive = 0;

    // Thin strip: single-line height, icon + label inline.
    const float strip_btn_w = BUTTON_HEIGHT * 2.0f;

    // Render one icon button in the strip; returns true if clicked.
    auto draw_strip_btn = [&](int slot_id, const char* icon_fa, const char* drive_label, bool has_disk) -> bool {
        bool selected = (qs_expanded_drive == slot_id);
        int style_pushes = 0;
        if (selected) {
            ImGui::PushStyleColor(ImGuiCol_Button, ImGui::GetStyle().Colors[ImGuiCol_ButtonActive]);
            ++style_pushes;
        } else if (has_disk) {
            ImVec4 c = ImGui::GetStyle().Colors[ImGuiCol_Button];
            c.y = ImClamp(c.y + 0.18f, 0.0f, 1.0f);  // subtle green tint when disk is loaded
            ImGui::PushStyleColor(ImGuiCol_Button, c);
            ++style_pushes;
        }
        char btn_id[64];
        snprintf(btn_id, sizeof(btn_id), "%s %s##qs_strip_%d", icon_fa, drive_label, slot_id);
        bool clicked = ImGui::Button(btn_id, ImVec2(strip_btn_w, BUTTON_HEIGHT));
        if (ImGui::IsItemHovered()) ImGui::SetTooltip("%s", drive_label);
        if (style_pushes > 0) ImGui::PopStyleColor(style_pushes);
        return clicked;
    };

    // DF0 (always present)
    if (draw_strip_btn(0, ICON_FA_FLOPPY_DISK, "DF0", std::strlen(changed_prefs.floppyslots[0].df) > 0))
        qs_expanded_drive = (qs_expanded_drive == 0) ? -1 : 0;

    if (df1_visible) {
        ImGui::SameLine();
        if (draw_strip_btn(1, ICON_FA_FLOPPY_DISK, "DF1", std::strlen(changed_prefs.floppyslots[1].df) > 0))
            qs_expanded_drive = (qs_expanded_drive == 1) ? -1 : 1;
    }
    if (df2_visible) {
        ImGui::SameLine();
        if (draw_strip_btn(2, ICON_FA_FLOPPY_DISK, "DF2", std::strlen(changed_prefs.floppyslots[2].df) > 0))
            qs_expanded_drive = (qs_expanded_drive == 2) ? -1 : 2;
    }
    if (df3_visible) {
        ImGui::SameLine();
        if (draw_strip_btn(3, ICON_FA_FLOPPY_DISK, "DF3", std::strlen(changed_prefs.floppyslots[3].df) > 0))
            qs_expanded_drive = (qs_expanded_drive == 3) ? -1 : 3;
    }
    if (cd_visible) {
        ImGui::SameLine();
        if (draw_strip_btn(4, ICON_FA_COMPACT_DISC, "CD", std::strlen(changed_prefs.cdslots[0].name) > 0))
            qs_expanded_drive = (qs_expanded_drive == 4) ? -1 : 4;
    }

    // "+" button — lets the user enable additional floppy drives
    const bool can_add_floppy = !cd_visible && (!df1_visible || !df2_visible || !df3_visible);
    if (can_add_floppy) {
        ImGui::SameLine();
        if (ImGui::Button(ICON_FA_PLUS "##qs_add_drive", ImVec2(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT)))
            ImGui::OpenPopup("qs_add_drive_popup");
        ShowHelpMarker("Enable additional floppy drives");
    }

    if (ImGui::BeginPopup("qs_add_drive_popup")) {
        ImGui::TextUnformatted("Enable additional drives:");
        ImGui::Separator();
        bool df1_en = changed_prefs.floppyslots[1].dfxtype != DRV_NONE;
        if (AmigaCheckbox("DF1", &df1_en)) {
            changed_prefs.floppyslots[1].dfxtype = df1_en ? DRV_35_DD : DRV_NONE;
            if (!df1_en) changed_prefs.floppyslots[1].df[0] = 0;
        }
        bool df2_en = changed_prefs.floppyslots[2].dfxtype != DRV_NONE;
        if (AmigaCheckbox("DF2", &df2_en)) {
            changed_prefs.floppyslots[2].dfxtype = df2_en ? DRV_35_DD : DRV_NONE;
            if (!df2_en) changed_prefs.floppyslots[2].df[0] = 0;
        }
        bool df3_en = changed_prefs.floppyslots[3].dfxtype != DRV_NONE;
        if (AmigaCheckbox("DF3", &df3_en)) {
            changed_prefs.floppyslots[3].dfxtype = df3_en ? DRV_35_DD : DRV_NONE;
            if (!df3_en) changed_prefs.floppyslots[3].df[0] = 0;
        }
        ImGui::EndPopup();
    }

    // === Expanded drive detail area ===
    if (qs_expanded_drive >= 0 && qs_expanded_drive <= 3) {
        // Show floppy detail for the selected slot
        bool slot_visible = (qs_expanded_drive == 0) ? true :
                            (qs_expanded_drive == 1) ? df1_visible :
                            (qs_expanded_drive == 2) ? df2_visible : df3_visible;
        bool is_editable  = (qs_expanded_drive == 0) ? df0_editable :
                            (qs_expanded_drive == 1) ? df1_editable : true;
        if (slot_visible) {
            // Compact quickstart row: enable checkbox + select + eject + path combo.
            ImGui::Separator();
            int i = qs_expanded_drive;
            ImGui::PushID(i);
            char label[64];
            snprintf(label, sizeof(label), "DF%d:", i);
            bool drive_enabled = changed_prefs.floppyslots[i].dfxtype != DRV_NONE;
            bool disk_present  = std::strlen(changed_prefs.floppyslots[i].df) > 0;

            if (!is_editable) ImGui::BeginDisabled();
            if (AmigaCheckbox(label, &drive_enabled)) {
                changed_prefs.floppyslots[i].dfxtype = drive_enabled ? DRV_35_DD : DRV_NONE;
                if (!drive_enabled) changed_prefs.floppyslots[i].df[0] = 0;
            }
            if (!is_editable) ImGui::EndDisabled();

            ImGui::SameLine();
            if (!drive_enabled) ImGui::BeginDisabled();
            if (AmigaButton(ICON_FA_FOLDER_OPEN " Select##QSFlopSel", ImVec2(BUTTON_WIDTH, 0))) {
                std::string tmp = disk_present ? changed_prefs.floppyslots[i].df
                                : (!last_floppy_dir.empty() ? last_floppy_dir : get_floppy_path());
                OpenFileDialogKey("QUICKSTART", "Select disk image file",
                    "All Supported Files (*.adf,*.adz,*.dms,*.ipf,*.zip,*.7z,*.lha,*.lzh,*.lzx,*.fdi,*.scp,*.wrp,*.dsq,*.gz,*.xz){.adf,.adz,.dms,.ipf,.zip,.7z,.lha,.lzh,.lzx,.fdi,.scp,.wrp,.dsq,.gz,.xz},All Files (*){.*}",
                    tmp);
                qs_pending_floppy_drive = i;
            }

            ImGui::SameLine();
            bool eject_enabled = drive_enabled && disk_present;
            if (!eject_enabled) ImGui::BeginDisabled();
            snprintf(label, sizeof(label), ICON_FA_EJECT "##QSFlopEj%d", i);
            if (AmigaButton(label, ImVec2(SMALL_BUTTON_WIDTH, 0))) {
                disk_eject(i);
                changed_prefs.floppyslots[i].df[0] = 0;
                qs_invalidate_wp_cache(i);
            }
            if (!eject_enabled) ImGui::EndDisabled();
            if (!drive_enabled) ImGui::EndDisabled();

            ImGui::SameLine();
            int selected_index = disk_present ? qs_find_in_mru(lstMRUDiskList, changed_prefs.floppyslots[i].df) : -1;
            std::vector<const char *> items;
            items.push_back("<empty>");
            items.reserve(qs_disk_display.size() + 1);
            for (auto &s : qs_disk_display) items.push_back(s.c_str());
            int combo_index = selected_index + 1;
            snprintf(label, sizeof(label), "##QSFlopPath%d", i);
            ImGui::PushItemWidth(-ImGui::GetStyle().ItemSpacing.x * 2.0f);
            if (ImGui::BeginCombo(label, items[combo_index])) {
                for (int n = 0; n < static_cast<int>(items.size()); n++) {
                    const bool is_selected = (combo_index == n);
                    if (is_selected)
                        ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
                    if (ImGui::Selectable(items[n], is_selected)) {
                        combo_index = n;
                        if (combo_index == 0) {
                            disk_eject(i);
                            changed_prefs.floppyslots[i].df[0] = 0;
                            qs_invalidate_wp_cache(i);
                        } else if (!qs_ignore_list_change && combo_index > 0 &&
                                   combo_index <= static_cast<int>(qs_disk_display.size())) {
                            std::string element = get_full_path_from_disk_list(qs_disk_display[combo_index - 1]);
                            if (element != changed_prefs.floppyslots[i].df) {
                                std::strncpy(changed_prefs.floppyslots[i].df, element.c_str(), MAX_DPATH);
                                DISK_history_add(changed_prefs.floppyslots[i].df, -1, HISTORY_FLOPPY, 0);
                                disk_insert(i, changed_prefs.floppyslots[i].df);
                                qs_invalidate_wp_cache(i);
                                if (!last_loaded_config[0])
                                    set_last_active_config(element.c_str());
                            }
                        }
                    }
                    if (is_selected) {
                        ImGui::PopStyleColor();
                        ImGui::SetItemDefaultFocus();
                    }
                }
                ImGui::EndCombo();
            }
            AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());
            ImGui::PopItemWidth();
            ImGui::PopID();
        }
    } else if (qs_expanded_drive == 4 && cd_visible) {
        // Show CD detail
        ImGui::Separator();
        ImGui::PushID("CD");

        const float cd_button_width = BUTTON_WIDTH * 1.33f;
        if (AmigaButton(ICON_FA_FOLDER_OPEN " Select file", ImVec2(cd_button_width, 0))) {
            std::string tmp;
            if (std::strlen(changed_prefs.cdslots[0].name) > 0)
                tmp = changed_prefs.cdslots[0].name;
            else
                tmp = get_cdrom_path();
            OpenFileDialogKey("QUICKSTART", "Select CD image file", "CD Images (*.cue,*.bin,*.iso,*.ccd,*.mds,*.chd,*.nrg){.cue,.bin,.iso,.ccd,.mds,.chd,.nrg},All Files (*){.*}", tmp);
            qs_pending_cd = true;
        }

        ImGui::SameLine();

        bool cd_controls_enabled = changed_prefs.cdslots[0].inuse;
        if (!cd_controls_enabled) ImGui::BeginDisabled();
        if (AmigaButton(ICON_FA_EJECT " Eject", ImVec2(BUTTON_WIDTH, 0))) {
            changed_prefs.cdslots[0].name[0] = 0;
            changed_prefs.cdslots[0].type = SCSI_UNIT_DEFAULT;
        }
        if (!cd_controls_enabled) ImGui::EndDisabled();

        int cd_index = -1;
        int cd_drive_count = 0;
        for (const auto &entry : qs_cd_display) {
            if (entry.rfind("/dev/", 0) == 0)
                cd_drive_count++;
            else
                break;
        }
        if (changed_prefs.cdslots[0].inuse && std::strlen(changed_prefs.cdslots[0].name) > 0) {
            if (changed_prefs.cdslots[0].type == SCSI_UNIT_DEFAULT) {
                const int mru_index = qs_find_in_mru(lstMRUCDList, changed_prefs.cdslots[0].name);
                if (mru_index >= 0)
                    cd_index = cd_drive_count + mru_index;
            } else if (changed_prefs.cdslots[0].type == SCSI_UNIT_IOCTL &&
                       std::strncmp(changed_prefs.cdslots[0].name, "/dev/", 5) == 0) {
                for (int i = 0; i < static_cast<int>(qs_cd_display.size()); ++i) {
                    if (qs_cd_display[i] == changed_prefs.cdslots[0].name) {
                        cd_index = i;
                        break;
                    }
                }
            }
        }

        std::vector<const char *> cd_items;
        cd_items.push_back("<empty>");
        cd_items.reserve(qs_cd_display.size() + 1);
        for (auto &s: qs_cd_display)
            cd_items.push_back(s.c_str());

        int combo_cd_index = cd_index + 1;
        ImGui::PushItemWidth(-ImGui::GetStyle().ItemSpacing.x * 2.0f);
        if (ImGui::BeginCombo("##CDImageCombo", cd_items[combo_cd_index])) {
            for (int n = 0; n < static_cast<int>(cd_items.size()); n++) {
                const bool is_selected = (combo_cd_index == n);
                if (is_selected)
                    ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
                if (ImGui::Selectable(cd_items[n], is_selected)) {
                    combo_cd_index = n;
                    if (combo_cd_index == 0) {
                        changed_prefs.cdslots[0].name[0] = 0;
                        changed_prefs.cdslots[0].type = SCSI_UNIT_DEFAULT;
                    } else if (!qs_ignore_list_change && combo_cd_index > 0 &&
                               combo_cd_index <= static_cast<int>(qs_cd_display.size())) {
                        const std::string &sel = qs_cd_display[combo_cd_index - 1];
                        if (sel.rfind("/dev/", 0) == 0) {
                            std::strncpy(changed_prefs.cdslots[0].name, sel.c_str(), MAX_DPATH);
                            changed_prefs.cdslots[0].inuse = true;
                            changed_prefs.cdslots[0].type = SCSI_UNIT_IOCTL;
                        } else {
                            std::string element = get_full_path_from_disk_list(sel);
                            if (element != changed_prefs.cdslots[0].name) {
                                std::strncpy(changed_prefs.cdslots[0].name, element.c_str(), MAX_DPATH);
                                DISK_history_add(changed_prefs.cdslots[0].name, -1, HISTORY_CD, 0);
                                changed_prefs.cdslots[0].inuse = true;
                                changed_prefs.cdslots[0].type = SCSI_UNIT_DEFAULT;
                                if (!last_loaded_config[0])
                                    set_last_active_config(element.c_str());
                            }
                        }
                    }
                }
                if (is_selected) {
                    ImGui::PopStyleColor();
                    ImGui::SetItemDefaultFocus();
                }
            }
            ImGui::EndCombo();
        }
        AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());
        ImGui::PopItemWidth();
        ImGui::PopID();
    }
    EndGroupBox("Emulated Drives");

    ImGui::Spacing();

    BeginGroupBox("WHDLoad auto-config:");
    if (AmigaButton(ICON_FA_FOLDER_OPEN " Select file##QSWHD", ImVec2(BUTTON_WIDTH * 1.33f, 0))) {
        std::string tmp;
        if (!whdload_prefs.whdload_filename.empty())
            tmp = whdload_prefs.whdload_filename;
        else
            tmp = get_whdload_arch_path();
        OpenFileDialogKey("QUICKSTART", "Select WHDLoad LHA file", ".lha,.lzh,.*", tmp);
        qs_pending_whd = true;
    }

    ImGui::SameLine();

    if (AmigaButton(ICON_FA_EJECT "##QSWHD", ImVec2(SMALL_BUTTON_WIDTH, 0))) {
        whdload_prefs.whdload_filename.clear();
    }

    std::vector<const char *> whd_items;
    whd_items.push_back("<empty>");
    whd_items.reserve(qs_whd_display.size() + 1);
    for (auto &s: qs_whd_display)
        whd_items.push_back(s.c_str());

    int whd_index = -1;
    if (!whdload_prefs.whdload_filename.empty())
        whd_index = qs_find_in_mru(lstMRUWhdloadList, whdload_prefs.whdload_filename.c_str());

    int combo_whd_index = whd_index + 1;
    ImGui::PushItemWidth(-ImGui::GetStyle().ItemSpacing.x * 2.0f);
    if (ImGui::BeginCombo("##WHDLoadCombo", whd_items[combo_whd_index])) {
        for (int n = 0; n < static_cast<int>(whd_items.size()); n++) {
            const bool is_selected = (combo_whd_index == n);
            if (is_selected)
                ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_HeaderActive]);
            if (ImGui::Selectable(whd_items[n], is_selected)) {
                combo_whd_index = n;
                if (combo_whd_index == 0) {
                    whdload_prefs.whdload_filename.clear();
                } else if (!qs_ignore_list_change && combo_whd_index > 0 && combo_whd_index <= static_cast<int>(
                               qs_whd_display.size())) {
                    std::string element = get_full_path_from_disk_list(qs_whd_display[combo_whd_index - 1]);
                    if (element != whdload_prefs.whdload_filename) {
                        whdload_prefs.whdload_filename = element;
                        add_file_to_mru_list(lstMRUWhdloadList, whdload_prefs.whdload_filename);
                    }
                    whdload_auto_prefs(&changed_prefs, whdload_prefs.whdload_filename.c_str());
                    set_last_active_config(whdload_prefs.whdload_filename.c_str());
                }
            }
            if (is_selected) {
                ImGui::PopStyleColor();
                ImGui::SetItemDefaultFocus();
            }
        }
        ImGui::EndCombo();
    }
    // Bevel around the combo frame
    AmigaBevel(ImGui::GetItemRectMin(), ImGui::GetItemRectMax(), ImGui::IsItemActive());

    ImGui::PopItemWidth();
    EndGroupBox("WHDLoad auto-config:");

    ImGui::Spacing();

    BeginGroupBox("Mode");
    bool qs_mode = amiberry_options.quickstart_start;
    if (AmigaCheckbox("Start in Quickstart mode", &qs_mode))
        amiberry_options.quickstart_start = qs_mode;
    ShowHelpMarker("Show this Quickstart panel when Amiberry starts");
    EndGroupBox("Mode");

    ImGui::Spacing();
    if (AmigaButton(ICON_FA_CHECK " Set Configuration", ImVec2(BUTTON_WIDTH * 2, BUTTON_HEIGHT))) {
        Quickstart_ApplyDefaults();
    }

    {
        std::string filePath;
        if (ConsumeFileDialogResultKey("QUICKSTART", filePath)) {
            if (qs_pending_floppy_drive >= 0 && qs_pending_floppy_drive < 4) {
                int i = qs_pending_floppy_drive;
                if (!filePath.empty()) {
                    size_t last_sep = filePath.find_last_of("/\\");
                    if (last_sep != std::string::npos)
                        last_floppy_dir = filePath.substr(0, last_sep);
                    int archive_count = populate_diskswapper_from_archive(filePath.c_str(), &changed_prefs);
                    if (archive_count > 0) {
                        std::strncpy(changed_prefs.floppyslots[i].df, changed_prefs.dfxlist[0], MAX_DPATH - 1);
                        changed_prefs.floppyslots[i].df[MAX_DPATH - 1] = '\0';
                        disk_insert(i, changed_prefs.floppyslots[i].df);
                        add_file_to_mru_list(lstMRUDiskList, std::string(changed_prefs.dfxlist[0]));
                        qs_invalidate_wp_cache(i);
                        if (!last_loaded_config[0])
                            set_last_active_config(changed_prefs.dfxlist[0]);
                    } else if (std::strncmp(changed_prefs.floppyslots[i].df, filePath.c_str(), MAX_DPATH) != 0) {
                        std::strncpy(changed_prefs.floppyslots[i].df, filePath.c_str(), MAX_DPATH - 1);
                        changed_prefs.floppyslots[i].df[MAX_DPATH - 1] = '\0';
                        disk_insert(i, filePath.c_str());
                        add_file_to_mru_list(lstMRUDiskList, filePath);
                        qs_invalidate_wp_cache(i);
                        if (!last_loaded_config[0])
                            set_last_active_config(filePath.c_str());
                    }
                }
            } else if (qs_pending_cd) {
                if (!filePath.empty()) {
                    if (std::strncmp(changed_prefs.cdslots[0].name, filePath.c_str(), MAX_DPATH) != 0) {
                        std::strncpy(changed_prefs.cdslots[0].name, filePath.c_str(), MAX_DPATH);
                        changed_prefs.cdslots[0].inuse = true;
                        changed_prefs.cdslots[0].type = SCSI_UNIT_DEFAULT;
                        add_file_to_mru_list(lstMRUCDList, filePath);
                        if (!last_loaded_config[0])
                            set_last_active_config(filePath.c_str());
                    }
                }
            } else if (qs_pending_whd) {
                if (!filePath.empty()) {
                    whdload_prefs.whdload_filename = filePath;
                    add_file_to_mru_list(lstMRUWhdloadList, whdload_prefs.whdload_filename);
                    whdload_auto_prefs(&changed_prefs, whdload_prefs.whdload_filename.c_str());
                    set_last_active_config(whdload_prefs.whdload_filename.c_str());
                }
            }

            qs_pending_floppy_drive = -1;
            qs_pending_cd = false;
            qs_pending_whd = false;
        }
    }
    ImGui::Unindent(5.0f);
}

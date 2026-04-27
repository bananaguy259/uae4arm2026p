package com.uae4arm2026;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.uae4arm2026.data.FileManager;
import com.uae4arm2026.data.UpstreamConfig;
import com.uae4arm2026.data.model.AmigaFile;
import com.uae4arm2026.data.model.FileCategory;
import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Uae4ArmEmulatorActivity extends SDLActivity {
	private OnBackInvokedCallback backCallback;
	private Uae4ArmVirtualKeyboard virtualKeyboard;
	private ImageButton keyboardButton;
	private ImageButton pauseButton;
	private ImageButton controllerButton;
	private boolean pauseMenuPausedEmulation = false;
	private boolean keepPausedForSubdialog = false;
	private boolean leavingEmulator = false;
	private final List<ParcelFileDescriptor> openFileDescriptors = new ArrayList<>();

	private static final String HINT_TRAP_BACK = "SDL_ANDROID_TRAP_BACK_BUTTON";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		cleanCache();
		super.onCreate(savedInstanceState);
		ensureVirtualKeyboardOverlay();
		if (shouldShowKeyboardButton()) {
			ensureKeyboardButtonOverlay();
		}
		ensureControllerButtonOverlay();
		ensurePauseButtonOverlay();
		enterImmersiveMode();
		registerBackHandler();
		applyControllerMappingsFromPrefs();
	}

	private void cleanCache() {
		File cacheDir = getCacheDir();
		File[] files = cacheDir.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory() && f.getName().startsWith("fd_")) {
					deleteRecursive(f);
				} else if (f.getName().startsWith("bridge_") || f.getName().contains(".translated")) {
					f.delete();
				}
			}
		}
	}

	private void deleteRecursive(File f) {
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) {
				for (File child : children) deleteRecursive(child);
			}
		}
		f.delete();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			enterImmersiveMode();
		}
	}

	@Override
	protected String[] getLibraries() {
		return new String[] { "uae4arm" };
	}

	@Override
	protected String[] getArguments() {
		Intent intent = getIntent();
		if (intent != null) {
			String[] args = intent.getStringArrayExtra("SDL_ARGS");
			if (args != null) {
				android.util.Log.d("Uae4Arm-SDL", "Original args: " + java.util.Arrays.toString(args));
				String[] translated = translateArguments(args);
				android.util.Log.d("Uae4Arm-SDL", "Final translated args: " + java.util.Arrays.toString(translated));
				return translated;
			}
		}
		return new String[0];
	}

	private String[] translateArguments(String[] args) {
		String[] result = new String[args.length];
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--config".equals(arg) && i + 1 < args.length) {
				result[i] = arg;
				result[i + 1] = translateConfigFile(args[i + 1]);
				i++;
				continue;
			}

			if (arg.contains("=") && !arg.startsWith("-")) {
				int eq = arg.indexOf('=');
				String key = arg.substring(0, eq);
				String value = arg.substring(eq + 1);

				if (isDirectoryKey(key)) {
					result[i] = arg;
					continue;
				}

				String translatedValue = translateStructuredValue(value);
				if (translatedValue != null) {
					result[i] = key + "=" + translatedValue;
				} else {
					result[i] = arg;
				}
			} else if (isExternalPath(arg)) {
				result[i] = translatePathToFd(arg);
			} else {
				result[i] = arg;
			}
		}
		return result;
	}

	private static final class ParsedPathValue {
		final String pathPart;
		final String suffix;

		ParsedPathValue(String pathPart, String suffix) {
			this.pathPart = pathPart;
			this.suffix = suffix;
		}
	}

	private ParsedPathValue parsePathValue(String value) {
		if (value.startsWith("\"")) {
			int start = 0;
			while (start < value.length() && value.charAt(start) == '"') {
				start++;
			}

			int endQuote = value.indexOf('"', start);
			if (endQuote >= start) {
				int suffixStart = endQuote;
				while (suffixStart < value.length() && value.charAt(suffixStart) == '"') {
					suffixStart++;
				}
				return new ParsedPathValue(value.substring(start, endQuote), value.substring(suffixStart));
			}

			return new ParsedPathValue(value.substring(start), "");
		}

		if (value.contains(",")) {
			int comma = value.lastIndexOf(',');
			return new ParsedPathValue(value.substring(0, comma), value.substring(comma));
		}

		return new ParsedPathValue(value, "");
	}

	private String translateStructuredValue(String value) {
		int firstQuote = value.indexOf('"');
		if (firstQuote >= 0) {
			int secondQuote = value.indexOf('"', firstQuote + 1);
			if (secondQuote > firstQuote + 1) {
				String pathPart = value.substring(firstQuote + 1, secondQuote);
				if (isExternalPath(pathPart)) {
					String translated = translatePathToFd(pathPart);
					return value.substring(0, firstQuote + 1) + translated + value.substring(secondQuote);
				}
			}
		}

		ParsedPathValue parsedValue = parsePathValue(value);
		if (isExternalPath(parsedValue.pathPart)) {
			String translated = translatePathToFd(parsedValue.pathPart);
			return translated + parsedValue.suffix;
		}

		return null;
	}

	private boolean isDirectoryKey(String key) {
		return key.endsWith("_path") || 
			   key.equals("whdload_arch_path") || 
			   key.equals("config_path") ||
			   key.equals("path_rom") ||
			   key.equals("filesystem") ||
			   key.equals("filesystem2");
	}

	private boolean isExternalPath(String path) {
		if (path == null || path.isEmpty()) return false;
		if (path.startsWith("content://")) return true;
		if (path.startsWith("/") && !FileManager.INSTANCE.isAppOwnedPath(this, path)) return true;
		return false;
	}

	private String translateConfigFile(String configPath) {
		File original = new File(configPath);
		if (!original.exists()) return configPath;

		try {
			List<String> lines = java.nio.file.Files.readAllLines(original.toPath());
			List<String> translatedLines = new ArrayList<>();
			boolean changed = false;

			for (String line : lines) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith(";") || !trimmed.contains("=")) {
					translatedLines.add(line);
					continue;
				}

				int eq = line.indexOf('=');
				String key = line.substring(0, eq).trim();
				String value = line.substring(eq + 1).trim();

				if (isDirectoryKey(key)) {
					translatedLines.add(line);
					continue;
				}

				String translatedValue = translateStructuredValue(value);
				if (translatedValue != null) {
					translatedLines.add(key + "=" + translatedValue);
					changed = true;
				} else {
					translatedLines.add(line);
				}
			}

			if (changed) {
				File translatedFile = new File(getCacheDir(), ".translated.uae");
				java.nio.file.Files.write(translatedFile.toPath(), translatedLines);
				return translatedFile.getAbsolutePath();
			}
		} catch (IOException e) {
			android.util.Log.e("Uae4Arm-SDL", "Failed to translate config: " + configPath, e);
		}
		return configPath;
	}

	private String translatePathToFd(String path) {
		if (path.startsWith("/proc/self/fd/") || path.startsWith(getCacheDir().getAbsolutePath())) return path;
		
		try {
			Uri contentUri = path.startsWith("content://") ? Uri.parse(path) : FileManager.INSTANCE.findContentUriForPath(this, path);
			if (contentUri == null) return path;

			ParcelFileDescriptor pfd;
			try {
				pfd = getContentResolver().openFileDescriptor(contentUri, "rw");
			} catch (Exception e) {
				try {
					pfd = getContentResolver().openFileDescriptor(contentUri, "r");
				} catch (Exception e2) {
					return path;
				}
			}

			if (pfd == null) {
				return path;
			}

			openFileDescriptors.add(pfd);
			int fd = pfd.getFd();
			String fileName = getRealFileName(contentUri, path);
			File bridgeDir = new File(getCacheDir(), "fd_" + fd);
			if (!bridgeDir.exists() && !bridgeDir.mkdirs()) {
				return path;
			}

			File bridgedFile = new File(bridgeDir, fileName);
			if (bridgedFile.exists()) bridgedFile.delete();
			android.system.Os.symlink("/proc/self/fd/" + fd, bridgedFile.getAbsolutePath());
			return bridgedFile.getAbsolutePath();
		} catch (Exception e) {
			android.util.Log.e("Uae4Arm-SDL", "Error bridging " + path, e);
		}
		return path;
	}

	private String getRealFileName(Uri uri, String fallback) {
		try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
			if (c != null && c.moveToFirst()) {
				int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
				if (i >= 0) return c.getString(i);
			}
		} catch (Exception ignored) {}
		int lastSlash = fallback.lastIndexOf('/');
		return lastSlash >= 0 ? fallback.substring(lastSlash + 1) : fallback;
	}

	private boolean pauseMenuVisible = false;

	private boolean isBackTrapped() {
		return SDLActivity.nativeGetHintBoolean(HINT_TRAP_BACK, false);
	}

	private void handleBackPress() {
		if (virtualKeyboard != null && virtualKeyboard.isKeyboardVisible()) {
			hideVirtualKeyboardFromNative();
			return;
		}
		if (isBackTrapped()) {
			showPauseMenu();
		} else {
			finish();
		}
	}

	private void showPauseMenu() {
		if (pauseMenuVisible) return;
		pauseMenuVisible = true;
		keepPausedForSubdialog = false;
		leavingEmulator = false;
		nativeSetPause(true);
		pauseMenuPausedEmulation = true;

		runOnUiThread(() -> {
			String[] options = {
				getString(R.string.pause_menu_resume),
				getString(R.string.pause_menu_swap_df0),
				getString(R.string.pause_menu_swap_df1),
				getString(R.string.pause_menu_detailed_settings),
				getString(R.string.pause_menu_help),
				getString(R.string.pause_menu_quit)
			};

			new AlertDialog.Builder(this)
				.setTitle(R.string.pause_menu_title)
				.setItems(options, (dialog, which) -> {
					switch (which) {
						case 0:
							break;
						case 1:
							keepPausedForSubdialog = true;
							showFloppyPicker(0);
							break;
						case 2:
							keepPausedForSubdialog = true;
							showFloppyPicker(1);
							break;
						case 3:
							leavingEmulator = true;
							openDetailedSettings();
							break;
						case 4:
							keepPausedForSubdialog = true;
							showHelpDialog();
							break;
						case 5:
							leavingEmulator = true;
							com.uae4arm2026.data.EmulatorLauncher.INSTANCE.writeCleanExitMarker(Uae4ArmEmulatorActivity.this);
							finish();
							break;
					}
				})
				.setOnDismissListener(d -> {
					pauseMenuVisible = false;
					if (!keepPausedForSubdialog && !leavingEmulator) {
						resumeFromPauseMenuIfNeeded();
					}
					enterImmersiveMode();
				})
				.show();
		});
	}

	private void showFloppyPicker(int drive) {
		String title = getString(drive == 0 ? R.string.pause_menu_pick_df0 : R.string.pause_menu_pick_df1);
		List<AmigaFile> floppies = FileManager.INSTANCE.scanForCategory(this, FileCategory.FLOPPIES);
		if (floppies.isEmpty()) {
			showEmptyMediaDialog(title, getString(R.string.quick_start_no_floppy_images));
			return;
		}

		String[] labels = new String[floppies.size()];
		for (int i = 0; i < floppies.size(); i++) {
			labels[i] = floppies.get(i).getName();
		}

		new AlertDialog.Builder(this)
			.setTitle(title)
			.setItems(labels, (dialog, which) -> {
				String path = floppies.get(which).getPath();
				String translated = translatePathToFd(path);
				nativeInsertFloppy(drive, translated);
			})
			.setNeutralButton(R.string.action_eject, (dialog, which) -> nativeEjectFloppy(drive))
			.setNegativeButton(android.R.string.cancel, null)
			.setOnDismissListener(d -> completePausedSubdialogFlow())
			.show();
	}

	private void showEmptyMediaDialog(String title, String message) {
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(android.R.string.ok, null)
			.setNegativeButton(R.string.pause_menu_detailed_settings, (dialog, which) -> {
				leavingEmulator = true;
				openDetailedSettings();
			})
			.setOnDismissListener(d -> completePausedSubdialogFlow())
			.show();
	}

	private void showHelpDialog() {
		new AlertDialog.Builder(this)
			.setTitle(R.string.help_dialog_title)
			.setMessage(android.text.Html.fromHtml(getString(R.string.help_dialog_message), android.text.Html.FROM_HTML_MODE_COMPACT))
			.setPositiveButton(android.R.string.ok, null)
			.setOnDismissListener(d -> completePausedSubdialogFlow())
			.show();
	}

	private void completePausedSubdialogFlow() {
		keepPausedForSubdialog = false;
		if (!leavingEmulator) {
			resumeFromPauseMenuIfNeeded();
		}
		enterImmersiveMode();
	}

	private final java.util.HashMap<Integer, Integer> kbButtonMap = new java.util.HashMap<>();

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1001) {
			applyControllerMappingsFromPrefs();
		}
	}

	private void applyControllerMappingsFromPrefs() {
		SharedPreferences prefs = getSharedPreferences("controller_map", MODE_PRIVATE);
		int[] sdlToTarget = new int[21];
		java.util.Arrays.fill(sdlToTarget, -1);
		for (int t = 0; t < 7; t++) {
			int androidKeycode = prefs.getInt("cd32_" + t, -1);
			if (androidKeycode >= 0) {
				int sdlBtn = ControllerMapActivity.androidToSdlButton(androidKeycode);
				if (sdlBtn >= 0 && sdlBtn < sdlToTarget.length) {
					sdlToTarget[sdlBtn] = t;
				}
			}
		}
		nativeApplyControllerMapping(sdlToTarget);

		int oscMode = prefs.getInt("onscreen_mode", 0);
		nativeSetOnScreenController(oscMode);

		kbButtonMap.clear();
		int[] extraButtons = {
			KeyEvent.KEYCODE_BUTTON_SELECT,
			KeyEvent.KEYCODE_BUTTON_L2,
			KeyEvent.KEYCODE_BUTTON_R2,
			KeyEvent.KEYCODE_BUTTON_THUMBL,
			KeyEvent.KEYCODE_BUTTON_THUMBR
		};
		for (int i = 0; i < extraButtons.length; i++) {
			int amigaKey = prefs.getInt("key_" + i, -1);
			if (amigaKey >= 0) {
				kbButtonMap.put(extraButtons[i], amigaKey);
			}
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int keyCode = event.getKeyCode();
		if (kbButtonMap.containsKey(keyCode)) {
			int amigaKey = kbButtonMap.get(keyCode);
			if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
				nativeSendAmigaKey(amigaKey, 1);
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				nativeSendAmigaKey(amigaKey, 0);
			}
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	private void resumeFromPauseMenuIfNeeded() {
		if (pauseMenuPausedEmulation) {
			nativeSetPause(false);
			pauseMenuPausedEmulation = false;
		}
	}

	private void openDetailedSettings() {
		hideVirtualKeyboardFromNative();
		com.uae4arm2026.data.EmulatorLauncher.INSTANCE.writeCleanExitMarker(this);
		Intent intent = new Intent(this, com.uae4arm2026.ui.MainActivity.class);
		intent.putExtra(com.uae4arm2026.ui.MainActivity.EXTRA_OPEN_ROUTE, com.uae4arm2026.ui.navigation.Screen.Settings.INSTANCE.getRoute());
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		startActivity(intent);
		finish();
	}

	private void ensureVirtualKeyboardOverlay() {
		if (virtualKeyboard != null) {
			return;
		}
		virtualKeyboard = new Uae4ArmVirtualKeyboard(this);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.gravity = Gravity.BOTTOM;
		addContentView(virtualKeyboard, params);
	}

	private boolean shouldShowKeyboardButton() {
		String[] args = getArguments();
		String configPath = null;
		for (int i = 0; i < args.length - 1; i++) {
			if ("--config".equals(args[i])) {
				configPath = args[i + 1];
				break;
			}
		}
		if (configPath == null || configPath.isEmpty()) {
			return true;
		}

		File configFile = new File(configPath);
		if (!configFile.exists()) {
			return true;
		}

		try {
			List<String> lines = java.nio.file.Files.readAllLines(configFile.toPath());
			Boolean androidKeyboardButtonEnabled = null;
			Boolean nativeKeyboardEnabled = null;
			for (String line : lines) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith(";") || !trimmed.contains("=")) {
					continue;
				}
				int eq = trimmed.indexOf('=');
				String key = trimmed.substring(0, eq).trim();
				String value = trimmed.substring(eq + 1).trim();
				if (UpstreamConfig.KEY_SHOW_ANDROID_KEYBOARD_BUTTON.equals(key)) {
					androidKeyboardButtonEnabled = parseConfigBoolean(value, true);
					continue;
				}
				if (UpstreamConfig.KEY_DEFAULT_OSK.equals(key) || UpstreamConfig.KEY_VIRTUAL_KEYBOARD_ENABLED.equals(key)) {
					nativeKeyboardEnabled = parseConfigBoolean(value, true);
				}
			}
			if (androidKeyboardButtonEnabled != null) {
				return androidKeyboardButtonEnabled;
			}
			if (nativeKeyboardEnabled != null) {
				return nativeKeyboardEnabled;
			}
		} catch (IOException ignored) {
			return true;
		}

		return true;
	}

	private boolean parseConfigBoolean(String value, boolean defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "1".equals(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "0".equals(value)) {
			return false;
		}
		return defaultValue;
	}

	private void ensureKeyboardButtonOverlay() {
		if (keyboardButton != null) {
			return;
		}

		final float density = getResources().getDisplayMetrics().density;
		keyboardButton = new ImageButton(this);
		keyboardButton.setImageResource(android.R.drawable.ic_menu_edit);
		keyboardButton.setColorFilter(0xFFFFFFFF);
		keyboardButton.setContentDescription(getString(R.string.pause_menu_keyboard));
		keyboardButton.setBackground(createPauseButtonBackground(density));
		keyboardButton.setAlpha(0.88f);
		keyboardButton.setOnClickListener(v -> toggleVirtualKeyboardFromNative());

		int size = (int) (44 * density);
		int margin = (int) (12 * density);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
		params.gravity = Gravity.BOTTOM | Gravity.END;
		params.bottomMargin = margin;
		params.rightMargin = margin;
		addContentView(keyboardButton, params);
	}

	private void ensureControllerButtonOverlay() {
		if (controllerButton != null) {
			return;
		}

		final float density = getResources().getDisplayMetrics().density;
		controllerButton = new ImageButton(this);
		controllerButton.setImageResource(R.drawable.ic_gamepad);
		controllerButton.setColorFilter(0xFFFFFFFF);
		controllerButton.setContentDescription(getString(R.string.ext_controller_content_desc));
		controllerButton.setBackground(createPauseButtonBackground(density));
		controllerButton.setAlpha(0.88f);
		controllerButton.setOnClickListener(v -> {
			Intent mapIntent = new Intent(Uae4ArmEmulatorActivity.this, ControllerMapActivity.class);
			startActivityForResult(mapIntent, 1001);
		});

		int size = (int) (44 * density);
		int margin = (int) (12 * density);
		int gap = (int) (8 * density);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
		params.gravity = Gravity.BOTTOM | Gravity.END;
		params.bottomMargin = margin + size + gap;
		params.rightMargin = margin;
		addContentView(controllerButton, params);
	}

	private void ensurePauseButtonOverlay() {
		if (pauseButton != null) {
			return;
		}

		final float density = getResources().getDisplayMetrics().density;
		pauseButton = new ImageButton(this);
		pauseButton.setImageResource(android.R.drawable.ic_media_pause);
		pauseButton.setColorFilter(0xFFFFFFFF);
		pauseButton.setContentDescription(getString(R.string.pause_menu_title));
		pauseButton.setBackground(createPauseButtonBackground(density));
		pauseButton.setAlpha(0.88f);
		pauseButton.setOnClickListener(v -> showPauseMenu());

		int size = (int) (44 * density);
		int margin = (int) (12 * density);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
		params.gravity = Gravity.TOP | Gravity.END;
		params.topMargin = margin;
		params.rightMargin = margin;
		addContentView(pauseButton, params);
	}

	private GradientDrawable createPauseButtonBackground(float density) {
		GradientDrawable background = new GradientDrawable();
		background.setShape(GradientDrawable.OVAL);
		background.setColor(0xA8202020);
		background.setStroke((int) (1.5f * density), 0x99FFFFFF);
		return background;
	}

	public void toggleVirtualKeyboardFromNative() {
		runOnUiThread(() -> {
			ensureVirtualKeyboardOverlay();
			virtualKeyboard.toggle();
			enterImmersiveMode();
		});
	}

	public void hideVirtualKeyboardFromNative() {
		runOnUiThread(() -> {
			if (virtualKeyboard != null) {
				virtualKeyboard.hide();
			}
			enterImmersiveMode();
		});
	}

	public static native void nativeSendAmigaKey(int keycode, int pressed);
	public static native void nativeSetPause(boolean paused);
	public static native void nativeInsertFloppy(int drive, String path);
	public static native void nativeEjectFloppy(int drive);
	public static native void nativeSetOnScreenController(int mode);
	public static native void nativeSetExternalControllerMode(int mode);
	public static native void nativeApplyControllerMapping(int[] sdlToTarget);

	private void registerBackHandler() {
		if (Build.VERSION.SDK_INT >= 33) {
			backCallback = this::handleBackPress;
			getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onBackPressed() {
		handleBackPress();
	}

	@Override
	protected void onDestroy() {
		resumeFromPauseMenuIfNeeded();
		if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
			getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
			backCallback = null;
		}
		final boolean finishing = isFinishing();
		if (finishing) {
			com.uae4arm2026.data.EmulatorLauncher.INSTANCE.writeCleanExitMarker(this);
			com.uae4arm2026.data.EmulatorLauncher.INSTANCE.clearSessionMarker(this);
		}
		super.onDestroy();
		for (ParcelFileDescriptor pfd : openFileDescriptors) {
			try {
				pfd.close();
			} catch (IOException ignored) {}
		}
		openFileDescriptors.clear();
		if (finishing) {
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	}

	private void enterImmersiveMode() {
		WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
		WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
		controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
}

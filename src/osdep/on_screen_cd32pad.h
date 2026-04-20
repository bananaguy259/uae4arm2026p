#ifndef ON_SCREEN_CD32PAD_H
#define ON_SCREEN_CD32PAD_H

#include <SDL3/SDL.h>

// Initialize on-screen CD32 pad surfaces and state.
// When USE_OPENGL is defined, renderer can be NULL (GL textures are created lazily).
void on_screen_cd32pad_init(SDL_Renderer* renderer);

// Clean up textures/surfaces and state.
void on_screen_cd32pad_quit();

// Render the on-screen CD32 pad controls using SDL renderer.
void on_screen_cd32pad_redraw(SDL_Renderer* renderer);

#ifdef USE_OPENGL
// Render the on-screen CD32 pad controls using OpenGL.
void on_screen_cd32pad_redraw_gl(int drawable_w, int drawable_h, const SDL_Rect& game_rect);
#endif

// Touch event handlers. Return true if the event was consumed.
bool on_screen_cd32pad_handle_finger_down(const SDL_Event& event, int window_w, int window_h);
bool on_screen_cd32pad_handle_finger_up(const SDL_Event& event, int window_w, int window_h);
bool on_screen_cd32pad_handle_finger_motion(const SDL_Event& event, int window_w, int window_h);

// Query / set enabled state
bool on_screen_cd32pad_is_enabled();
void on_screen_cd32pad_set_enabled(bool enabled);

// Update the control layout when screen geometry changes.
void on_screen_cd32pad_update_layout(int screen_w, int screen_h, const SDL_Rect& game_rect);

#endif // ON_SCREEN_CD32PAD_H

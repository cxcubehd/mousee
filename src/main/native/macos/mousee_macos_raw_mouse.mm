#import <Foundation/Foundation.h>
#import <GameController/GameController.h>

#include <jni.h>
#include <math.h>
#include <os/lock.h>
#include <stdatomic.h>
#include <stdint.h>

static id mousee_connect_observer = nil;
static id mousee_disconnect_observer = nil;
static dispatch_queue_t mousee_handler_queue = nil;

static atomic_bool mousee_initialized = false;
static atomic_bool mousee_supported = false;
static atomic_bool mousee_relative_mode = false;
static atomic_bool mousee_diagnostics = false;
static atomic_int mousee_connected_mice = 0;

static os_unfair_lock mousee_delta_lock = OS_UNFAIR_LOCK_INIT;
static double mousee_pending_dx = 0.0;
static double mousee_pending_dy = 0.0;
static double mousee_total_abs_dx = 0.0;
static double mousee_total_abs_dy = 0.0;
static uint64_t mousee_pending_events = 0;
static uint64_t mousee_total_events = 0;
static double mousee_peak_abs_dx = 0.0;
static double mousee_peak_abs_dy = 0.0;

static void mousee_reset_motion_state(void) {
    os_unfair_lock_lock(&mousee_delta_lock);
    mousee_pending_dx = 0.0;
    mousee_pending_dy = 0.0;
    mousee_total_abs_dx = 0.0;
    mousee_total_abs_dy = 0.0;
    mousee_pending_events = 0;
    mousee_total_events = 0;
    mousee_peak_abs_dx = 0.0;
    mousee_peak_abs_dy = 0.0;
    os_unfair_lock_unlock(&mousee_delta_lock);
}

static void mousee_refresh_mouse_count(void) API_AVAILABLE(macos(14.0)) {
    atomic_store_explicit(&mousee_connected_mice, (int)[GCMouse mice].count, memory_order_release);
}

static void mousee_attach_mouse(GCMouse *mouse) API_AVAILABLE(macos(14.0)) {
    if (mouse.mouseInput == nil) {
        mousee_refresh_mouse_count();
        return;
    }

    if (mousee_handler_queue != nil) {
        mouse.handlerQueue = mousee_handler_queue;
    }

    mouse.mouseInput.mouseMovedHandler = ^(GCMouseInput *mouseInput, float deltaX, float deltaY) {
      if (!atomic_load_explicit(&mousee_relative_mode, memory_order_acquire)) {
          return;
      }

      double dx = (double)deltaX;
      double dy = -(double)deltaY;
      double absDx = fabs(dx);
      double absDy = fabs(dy);

      os_unfair_lock_lock(&mousee_delta_lock);
      mousee_pending_dx += dx;
      mousee_pending_dy += dy;
      mousee_total_abs_dx += absDx;
      mousee_total_abs_dy += absDy;
      mousee_pending_events++;
      mousee_total_events++;
      if (absDx > mousee_peak_abs_dx) {
          mousee_peak_abs_dx = absDx;
      }
      if (absDy > mousee_peak_abs_dy) {
          mousee_peak_abs_dy = absDy;
      }
      os_unfair_lock_unlock(&mousee_delta_lock);
    };

    mousee_refresh_mouse_count();
}

static void mousee_detach_mouse(GCMouse *mouse) API_AVAILABLE(macos(14.0)) {
    if (mouse.mouseInput != nil) {
        mouse.mouseInput.mouseMovedHandler = nil;
    }
    mousee_refresh_mouse_count();
}

extern "C" JNIEXPORT jboolean JNICALL Java_dev_chrones_platform_MacosRawMouseNative_init(
    JNIEnv *env, jclass clazz, jboolean diagnostics) {
    (void)env;
    (void)clazz;

    atomic_store_explicit(&mousee_diagnostics, diagnostics == JNI_TRUE, memory_order_release);
    if (atomic_load_explicit(&mousee_initialized, memory_order_acquire)) {
        return atomic_load_explicit(&mousee_supported, memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
    }

    @autoreleasepool {
        atomic_store_explicit(&mousee_initialized, true, memory_order_release);

        if (@available(macOS 14.0, *)) {
            mousee_handler_queue =
                dispatch_queue_create("dev.chrones.mousee.raw-mouse", DISPATCH_QUEUE_SERIAL);
            dispatch_set_target_queue(
                mousee_handler_queue, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0));

            NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
            mousee_connect_observer = [center addObserverForName:GCMouseDidConnectNotification
                                                          object:nil
                                                           queue:nil
                                                      usingBlock:^(NSNotification *note) {
                                                        GCMouse *mouse = (GCMouse *)note.object;
                                                        mousee_attach_mouse(mouse);
                                                      }];

            mousee_disconnect_observer = [center addObserverForName:GCMouseDidDisconnectNotification
                                                             object:nil
                                                              queue:nil
                                                         usingBlock:^(NSNotification *note) {
                                                           GCMouse *mouse = (GCMouse *)note.object;
                                                           mousee_detach_mouse(mouse);
                                                         }];

            for (GCMouse *mouse in [GCMouse mice]) {
                mousee_attach_mouse(mouse);
            }

            mousee_refresh_mouse_count();
            atomic_store_explicit(&mousee_supported, true, memory_order_release);
            return JNI_TRUE;
        }

        atomic_store_explicit(&mousee_supported, false, memory_order_release);
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL Java_dev_chrones_platform_MacosRawMouseNative_isSupported(
    JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return atomic_load_explicit(&mousee_supported, memory_order_acquire) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL Java_dev_chrones_platform_MacosRawMouseNative_hasMouse(
    JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return atomic_load_explicit(&mousee_connected_mice, memory_order_acquire) > 0 ? JNI_TRUE
                                                                                  : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL Java_dev_chrones_platform_MacosRawMouseNative_setRelativeMode(
    JNIEnv *env, jclass clazz, jboolean enabled) {
    (void)env;
    (void)clazz;
    atomic_store_explicit(&mousee_relative_mode, enabled == JNI_TRUE, memory_order_release);
}

extern "C" JNIEXPORT jint JNICALL Java_dev_chrones_platform_MacosRawMouseNative_poll(
    JNIEnv *env, jclass clazz, jdoubleArray output) {
    (void)clazz;

    if (output == nullptr || (*env).GetArrayLength(output) < 4) {
        return 0;
    }

    double values[4];
    uint64_t events;

    os_unfair_lock_lock(&mousee_delta_lock);
    values[0] = mousee_pending_dx;
    values[1] = mousee_pending_dy;
    events = mousee_pending_events;
    values[2] = (double)events;
    values[3] = (double)atomic_load_explicit(&mousee_connected_mice, memory_order_acquire);
    mousee_pending_dx = 0.0;
    mousee_pending_dy = 0.0;
    mousee_pending_events = 0;
    os_unfair_lock_unlock(&mousee_delta_lock);

    (*env).SetDoubleArrayRegion(output, 0, 4, values);
    return events > (uint64_t)INT32_MAX ? INT32_MAX : (jint)events;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_chrones_platform_MacosRawMouseNative_setDiagnosticLogging(
    JNIEnv *env, jclass clazz, jboolean enabled) {
    (void)env;
    (void)clazz;
    atomic_store_explicit(&mousee_diagnostics, enabled == JNI_TRUE, memory_order_release);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_chrones_platform_MacosRawMouseNative_diagnosticSummary(JNIEnv *env, jclass clazz) {
    (void)clazz;

    double totalAbsDx;
    double totalAbsDy;
    double peakAbsDx;
    double peakAbsDy;
    uint64_t totalEvents;

    os_unfair_lock_lock(&mousee_delta_lock);
    totalAbsDx = mousee_total_abs_dx;
    totalAbsDy = mousee_total_abs_dy;
    peakAbsDx = mousee_peak_abs_dx;
    peakAbsDy = mousee_peak_abs_dy;
    totalEvents = mousee_total_events;
    os_unfair_lock_unlock(&mousee_delta_lock);

    NSString *summary =
        [NSString stringWithFormat:@"supported=%@ relative=%@ diagnostics=%@ mice=%d "
                                   @"totalEvents=%llu totalAbs=(%.2f, %.2f) "
                                   @"peakEventAbs=(%.2f, %.2f)",
            atomic_load_explicit(&mousee_supported, memory_order_acquire) ? @"true" : @"false",
            atomic_load_explicit(&mousee_relative_mode, memory_order_acquire) ? @"true" : @"false",
            atomic_load_explicit(&mousee_diagnostics, memory_order_acquire) ? @"true" : @"false",
            atomic_load_explicit(&mousee_connected_mice, memory_order_acquire),
            (unsigned long long)totalEvents, totalAbsDx, totalAbsDy, peakAbsDx, peakAbsDy];

    return (*env).NewStringUTF([summary UTF8String]);
}

extern "C" JNIEXPORT void JNICALL Java_dev_chrones_platform_MacosRawMouseNative_shutdown(
    JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;

    atomic_store_explicit(&mousee_relative_mode, false, memory_order_release);

    @autoreleasepool {
        if (@available(macOS 14.0, *)) {
            NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
            if (mousee_connect_observer != nil) {
                [center removeObserver:mousee_connect_observer];
                mousee_connect_observer = nil;
            }
            if (mousee_disconnect_observer != nil) {
                [center removeObserver:mousee_disconnect_observer];
                mousee_disconnect_observer = nil;
            }

            for (GCMouse *mouse in [GCMouse mice]) {
                mousee_detach_mouse(mouse);
            }
        }
    }

    atomic_store_explicit(&mousee_supported, false, memory_order_release);
    atomic_store_explicit(&mousee_initialized, false, memory_order_release);
    atomic_store_explicit(&mousee_connected_mice, 0, memory_order_release);
    mousee_handler_queue = nil;
    mousee_reset_motion_state();
}

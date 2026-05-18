// app/src/main/cpp/jni_interface.h
#ifndef JNI_INTERFACE_H
#define JNI_INTERFACE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_com_jpbjq_midilib_midi_midiEditor_setMidiInstrument(JNIEnv*, jobject, jint);

#ifdef __cplusplus
}
#endif

#endif // JNI_INTERFACE_H
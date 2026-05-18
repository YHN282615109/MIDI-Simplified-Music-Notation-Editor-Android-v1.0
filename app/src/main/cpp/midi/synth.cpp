#include "synth.h"


#include "jni_interface.h"

JNIEXPORT void JNICALL Java_com_jpbjq_midilib_midi_midiEditor_setMidiInstrument(JNIEnv* env, jobject thiz, jint instrument) {
    // 实现设置乐器的逻辑
}


namespace mgnr{

synth::~synth(){
    clearTracks();
}

void synth::onNoteOn(note * n,int c){
    if(!n->info.empty()){
        if(n->info[0]=='@')
            return;
    }
    callJavaNoteOn(n->info.c_str(),c, n->tone, n->volume);
}

void synth::onNoteOff(note * n,int c){
    if(!n->info.empty()){
        if(n->info[0]=='@')
            return;
    }
    callJavaNoteOff(n->info.c_str(),c, n->tone);
}
void synth::onUseInfo(const std::string & info){
    if(info.empty())
        return;
    if(info[0]=='@')
        return;
    onLoadName(info);
    loadInstrument(info);
}

}




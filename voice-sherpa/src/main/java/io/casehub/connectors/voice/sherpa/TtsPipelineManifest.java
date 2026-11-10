package io.casehub.connectors.voice.sherpa;

public sealed interface TtsPipelineManifest permits CosyVoice3Manifest {
    PipelineHeader header();
}

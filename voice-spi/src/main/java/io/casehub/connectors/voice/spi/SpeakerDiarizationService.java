package io.casehub.connectors.voice.spi;

import io.casehub.connectors.voice.model.DiarizationOptions;
import io.casehub.connectors.voice.model.DiarizedSegment;

import java.nio.file.Path;
import java.util.List;

public interface SpeakerDiarizationService {
    List<DiarizedSegment> diarize(Path audioFile, DiarizationOptions options);
}

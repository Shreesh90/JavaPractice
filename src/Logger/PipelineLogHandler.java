package Logger;

import java.util.List;

public class PipelineLogHandler implements LogHandler {
    List<Transformer> transformers;
    Destination destination;

    public PipelineLogHandler(List<Transformer> transformers, Destination destination) {
        this.destination = destination;
        this.transformers = transformers;
    }

    @Override
    public void log(String msg) {
        for(Transformer t: transformers) {
            msg = t.transform(msg);
        }
        destination.write(msg);
    }
}

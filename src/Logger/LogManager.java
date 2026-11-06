package Logger;

import java.util.ArrayList;
import java.util.List;

public class LogManager {
    public static class FilterTransformer implements Transformer {

        public FilterTransformer(){}
        @Override
        public String transform(String message) {
            return "";
        }
    }

    public static class TruncateTransformer implements Transformer {
        private int N;
        public TruncateTransformer(int N){
            this.N = N;
        }
        @Override
        public String transform(String message) {
            return "";
        }
    }

    public static class UppercaseTransformer implements Transformer {

        public UppercaseTransformer(){}
        @Override
        public String transform(String message) {
            return "";
        }
    }

    public static class ConsoleDestination implements Destination {

        @Override
        public void write(String message) {
        }
    }

    public static class ArrayDestination implements Destination {
        List<String> temp = new ArrayList<>();
        @Override
        public void write(String message) {
        }
    }

    private List<LogHandler> logHandlers = new ArrayList<>();
    public LogManager(){}

    public void log(String message) {
        for(LogHandler lh: logHandlers) {
            lh.log(message);
        }
    }

    public void addHandler(LogHandler logHandler) {
        logHandlers.add(logHandler);
    }

    public static void main(String[] args) {
        LogManager logger = new LogManager();
        List<Transformer> tl = new ArrayList<>(List.of(new UppercaseTransformer(), new TruncateTransformer(5), new FilterTransformer()));
        PipelineLogHandler lh = new PipelineLogHandler(tl, new ConsoleDestination());
        logger.addHandler(lh);

        logger.log("hello");
    }
}

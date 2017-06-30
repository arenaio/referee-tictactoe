package io.github.arenaio.base;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

/**
 * @author daniel.laeppchen on 30.06.17.
 */
public abstract class MultiReferee extends AbstractReferee{
    private Properties properties;

    public MultiReferee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        super(is, out, err);
    }

    @Override
    protected final void handleInitInputForReferee(int playerCount, String[] init) throws InvalidFormatException {
        properties = new Properties();
        try {
            for (String s : init) {
                properties.load(new StringReader(s));
            }
        } catch (IOException e) {
        }
        initReferee(playerCount, properties);
        properties = getConfiguration();
    }

    abstract protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException;

    abstract protected Properties getConfiguration();

    protected void appendDataToEnd(PrintStream stream) throws IOException {
        stream.println(OutputCommand.UINPUT.format(properties.size()));
        for (Map.Entry<Object, Object> t : properties.entrySet()) {
            stream.println(t.getKey() + "=" + t.getValue());
        }
    }
}

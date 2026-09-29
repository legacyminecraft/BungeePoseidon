package net.md_5.bungee;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.command.ConsoleCommandSender;
import org.jline.reader.UserInterruptException;

import java.util.Arrays;
import java.util.List;

public class Bootstrap {

    private static List<String> list(String... params) {
        return Arrays.asList(params);
    }

    /**
     * Starts a new instance of BungeeCord.
     *
     * @param args command line arguments, currently none are used
     * @throws Exception when the server cannot be started
     */
    public static void main(String[] args) throws Exception {
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.acceptsAll(list("v", "version"));

        OptionSet options = parser.parse(args);

        if (options.has("version")) {
            System.out.println(Bootstrap.class.getPackage().getImplementationVersion());
            return;
        }

        System.setProperty("java.net.preferIPv4Stack", "true");

        BungeeCord bungee = new BungeeCord();
        ProxyServer.setInstance(bungee);
        bungee.getLogger().info("Enabled BungeeCord version " + bungee.getFullVersion());
        bungee.start();

        try {
            while (bungee.isRunning) {
                String line = bungee.getLineReader().readLine("> ");
                if (line != null) {
                    if (!bungee.getPluginManager().dispatchCommand(ConsoleCommandSender.getInstance(), line)) {
                        bungee.getConsole().sendMessage(ChatColor.RED + "Command not found");
                    }
                }
            }
        } catch (UserInterruptException _) {
            bungee.stop();
        }
    }
}

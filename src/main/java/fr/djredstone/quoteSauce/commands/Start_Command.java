package fr.djredstone.quoteSauce.commands;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import fr.djredstone.quoteSauce.Main;
import fr.djredstone.quoteSauce.Setup;
import fr.djredstone.quoteSauce.Utils;
import fr.djredstone.quoteSauce.game.Game;
import org.javatuples.Quartet;
import org.javatuples.Quintet;
import org.yaml.snakeyaml.Yaml;

public class Start_Command extends ListenerAdapter {

    private static final String cmd = "start";
    private static final String arg = "[ID]";
    public Start_Command() { Help_Command.cmds.put(cmd + " " + arg, "Permet de commencer une partie"); }

    @SuppressWarnings("unchecked")
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {

        final String content = event.getMessage().getContentRaw().toLowerCase();
        final String[] args = content.split("\\s+");
        if (!content.startsWith(Main.prefix + (Main.devMode ? "test_" : "") + cmd)) return;

        if (Game.games.containsKey(event.getChannel().getId())) {
            Utils.replyEmbed(event, "Une partie est déjà en cours ! ⛔️", null);
            return;
        }

        Map<String, Object> map;
        if (args.length <= 1) {
            if (event.getMessage().getAttachments().isEmpty()) {
                Utils.replyEmbed(event, "Veuillez entrer une ID valable \uD83D\uDCCE", null);
                return;
            } else {
                Message.Attachment attachment = event.getMessage().getAttachments().get(0);
                if (attachment.getFileExtension() == null) {
                    Utils.replyEmbed(event, "Le thème personnalisé a un problème d'extension ❓", null);
                    return;
                }
                if (!attachment.getFileExtension().equalsIgnoreCase("yaml") && !attachment.getFileExtension().equalsIgnoreCase("yml")) {
                    Utils.replyEmbed(event, "Le thème personnalisé a un problème d'extension ❔", null);
                    return;
                }
                map = getGame(attachment.getUrl());
            }
        } else {
            if (!Setup.themeList.contains(args[1])) {
                Utils.replyEmbed(event, "Veuillez entrer une ID valable \uD83D\uDCCE", null);
                return;
            }
            map = getGame(args[1]);
        }

        if (map == null || !validYAML(map)) {
            Utils.replyEmbed(event, "Une erreur s'est produite \uD83E\uDD14", null);
            return;
        }
        String themeName = (String) map.get("name");
        int maxQuestionNumber = 0;
        ArrayList<Object> questions = (ArrayList<Object>) map.get("questions");
        for (Object key : questions) {
            final HashMap<String, Object> quote = (HashMap<String, Object>) key;
            final int number = (int) quote.get("number");
            if (number > maxQuestionNumber)
                maxQuestionNumber = number;
        }
        String[] messageID = new String[1];
        EmbedBuilder embed = Utils.getDefaultEmbed()
                .setFooter("Démarré par " + event.getAuthor().getAsTag(), event.getAuthor().getAvatarUrl())
                .addField("Une partie commence dans **30s** ! \uD83D\uDCAA Elle est sur le thème : __" + themeName + "__ \uD83D\uDC40",
                        "*Cliquez sur le bouton ci-dessous pour rejoindre la partie*", false);
        event.getChannel()
                .sendMessageEmbeds(embed.build())
                .setActionRow(Button.success((Main.devMode ? "TEST_" : "") + "join-game", "Rejoindre la partie \uD83C\uDF99"))
                .queue(message -> messageID[0] = message.getId());
        event.getMessage().delete().queue();
        HashMap<String, Integer> PlayersAndPoints = new HashMap<>();
        PlayersAndPoints.put(event.getAuthor().getId(), 0);
        Game.games.put(event.getChannel().getId(), new Quintet<>(map, PlayersAndPoints, new HashSet<>(), new Quartet<>(false, null, null, null), maxQuestionNumber));

        int finalMaxQuestionNumber = maxQuestionNumber;
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if (Game.games.get(event.getChannel().getId()).getValue1().size() < 2) {
                    Game.games.remove(event.getChannel().getId());
                    EmbedBuilder embed = Utils.getDefaultEmbed()
                            .setFooter("Démarré par " + event.getAuthor().getAsTag(), event.getAuthor().getAvatarUrl())
                            .addField("**Partie annulée !** \uD83D\uDED1",
                                    "Il faut au moins __" + Game.minmumPlayers + "__ joueurs pour commencer une partie.", false);
                    event.getChannel()
                            .editMessageEmbedsById(messageID[0], embed.build())
                            .setActionRow(Button.success("null", "Rejoindre la partie \uD83C\uDF99").asDisabled()).queue();
                } else {
                    System.out.println("Game started at channel " + event.getChannel().getId() + " with " + finalMaxQuestionNumber + " questions");
                    EmbedBuilder embed = Utils.getDefaultEmbed()
                            .setTitle("**La partie commence !** \uD83C\uDF99 (" + themeName + ")")
                            .setDescription("Les joueurs suivants participents :")
                            .setFooter("Démarré par " + event.getAuthor().getAsTag(), event.getAuthor().getAvatarUrl());

                    for (String userID : Game.games.get(event.getChannel().getId()).getValue1().keySet()) {
                        embed.addField("\uD83D\uDD38" + Main.jda.retrieveUserById(userID).complete().getAsTag(), "", true);
                    }
                    event.getChannel().editMessageEmbedsById(messageID[0], embed.build()).setActionRow(Button.success("null", "Rejoindre la partie \uD83C\uDF99").asDisabled()).queue();
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            try {
                                Game.startGame((TextChannel) event.getChannel());
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }, 5 * 1000);
                }
            }
        }, 30 * 1000);

    }

    private static Map<String, Object> getGame(String URLorNAME) {
        try {
            URL url = new URL(URLorNAME);
            InputStream stream = url.openStream();
            try {
                return new Yaml().load(stream);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } catch (MalformedURLException e) {
            try {
                return new Yaml().load(new FileInputStream("./themes/" + URLorNAME + ".yaml"));
            } catch (FileNotFoundException ex) {
                e.printStackTrace();
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static boolean validYAML(Map<String, Object> map) {
        if (!map.containsKey("name")) return false;
        ArrayList<Object> questions;
        try {
            questions = (ArrayList<Object>) map.get("questions");
            for (Object question : questions) {
                HashMap<String, Object> quote = (HashMap<String, Object>) question;
                int number = (int) quote.get("number");
                String sQuote = (String) quote.get("quote");
                ArrayList<Object> aswers = (ArrayList<Object>) quote.get("aswer");
                for (Object aswer : aswers) {
                    String sAswer = (String) aswer;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}

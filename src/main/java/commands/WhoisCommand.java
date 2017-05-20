package commands;

import data.Character;
import data.Constants;
import data.ServerDofus;
import discord.Message;
import exceptions.*;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sx.blah.discord.handle.obj.IMessage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.Exception;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by steve on 14/07/2016.
 */
public class WhoisCommand extends AbstractCommand{

    private final static Logger LOG = LoggerFactory.getLogger(WhoisCommand.class);

    private final static String forPseudo = "TEXT=";
    private final static String forServer = "character_homeserv[]=";
    private final static String forClass = "character_breed_id[]=";
    private final static String forSex = "character_sex[]=";
    private final static String forLevelMin = "character_level_min=";
    private final static String forLevelMax = "character_level_max=";
    private final static String forGuild = "guild_id[]=";

    public WhoisCommand(){
        super(Pattern.compile("whois"),
                Pattern.compile("^(" + Constants.prefixCommand + "whois)(\\s+[\\p{L}|-]+)(\\s+.+)?$"));
    }

    @Override
    public boolean request(IMessage message) {
        if (super.request(message)) {

            String pseudo = m.group(2).trim().toLowerCase();

            StringBuilder url = null;
            try {
                url = new StringBuilder(Constants.officialURL + Constants.characterPageURL)
                        .append("?").append(forPseudo).append(URLEncoder.encode(pseudo, "UTF-8"));

            } catch (UnsupportedEncodingException e) {
                ExceptionManager.manageException(e, message, this);
                return false;
            }

            if (m.group(3) != null){
                String serverName = m.group(3).trim().toLowerCase();
                List<ServerDofus> result = new ArrayList<>();

                for(ServerDofus server : ServerDofus.getServersDofus())
                    if (server.getName().toLowerCase().startsWith(serverName))
                        result.add(server);

                if (result.size() == 1)
                    url.append("&").append(forServer).append(result.get(0).getId());
                else {
                    if (! result.isEmpty())
                        new TooMuchServersException().throwException(message, this);
                    else
                        new ServerNotFoundException().throwException(message, this);
                    return false;
                }
            }

            try
            {
                Document doc = Jsoup.parse(new URL(url.toString()).openStream(), "UTF-8", url.toString());
                Elements elems = doc.getElementsByClass("ak-bg-odd");
                elems.addAll(doc.getElementsByClass("ak-bg-even"));

                if (!elems.isEmpty()) {
                    // on boucle jusqu'à temps de trouver le bon personnage (ie le plus proche du nom donnée)
                    List<String> result = new ArrayList<>();
                    List<String> servers = new ArrayList<>();

                    for (Element element : elems)
                        if (pseudo.equals(element.child(1).text().trim().toLowerCase())) {
                            result.add(element.child(1).select("a").attr("href"));
                            servers.add(element.child(element.children().size() - 2).text());
                        }

                    if (result.size() == 1) {

                        Connection.Response response = Jsoup.connect(Constants.officialURL + result.get(0))
                                .followRedirects(true).execute();

                        if (!response.url().getPath().endsWith("indisponible")) {
                            Character characPage = Character.getCharacter(Constants.officialURL + result.get(0));
                            Message.sendEmbed(message.getChannel(), characPage.getEmbedObject());
                        } else
                            new CharacterTooOldException().throwException(message, this);
                    }
                    else if (result.size() > 1)
                        new TooMuchCharactersException().throwException(message, this, servers);
                    else
                        new CharacterNotFoundException().throwException(message, this);
                }
                else
                    new CharacterNotFoundException().throwException(message, this);
            } catch (FileNotFoundException | HttpStatusException e){
                new CharacterPageNotFoundException().throwException(message, this);
            } catch(IOException e){
                ExceptionManager.manageIOException(e, message, this);
            }  catch (Exception e) {
                ExceptionManager.manageException(e, message, this);
            }
        }
        return false;
    }

    @Override
    public String help() {
        return "**" + Constants.prefixCommand + "whois** donne la page personnelle d'un personnage.";
    }

    @Override
    public String helpDetailed() {
        return help()
                + "\n`" + Constants.prefixCommand + "whois *pseudo`* : donne la page personnelle associée au pseudo. Celui-ci doit être exact."
                + "\n`" + Constants.prefixCommand + "whois *pseudo serveur`* : est à utiliser lorsque le pseudo ne suffit pas pour déterminer la fiche d'un personnage.\n";
    }
}

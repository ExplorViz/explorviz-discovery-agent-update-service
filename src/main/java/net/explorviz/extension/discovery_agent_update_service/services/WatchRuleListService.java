package net.explorviz.extension.discovery_agent_update_service.services;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.TimerTask;
import net.explorviz.extension.discovery_agent_update_service.model.RuleModel;
import org.jeasy.rules.mvel.MVELRuleFactory;
import org.jeasy.rules.support.YamlRuleDefinitionReader;
import org.slf4j.LoggerFactory;

/**
 * Watchs the rulefolder and updates the rulelist.
 */
public class WatchRuleListService extends TimerTask {

  private static final org.slf4j.Logger LOGGER =
      LoggerFactory.getLogger(WatchRuleListService.class);
  private static final String DIRECTORY = "Rules";
  private static final String PATH_DIRECTORY = DIRECTORY + File.separator;
  private static final String YML = ".yml";
  private WatchService watchService;
  private static ArrayList<RuleModel> ruleList;


  /**
   * Class for checking a directory, containing a list of rules.
   */
  public WatchRuleListService() {
    // To be sure we have a Rules folder
    new File(DIRECTORY).mkdir();
    WatchRuleListService.ruleList = new ArrayList<>();
    try {
      this.watchService = FileSystems.getDefault().newWatchService();
      final Path path = Paths.get(DIRECTORY);
      path.register(this.watchService, StandardWatchEventKinds.ENTRY_DELETE,
          StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
    } catch (final IOException e) {
      LOGGER.error("Can't create folder Rules.");

    }
    this.checkActList();
  }

  /**
   * Adds a rule to the rule list.
   *
   * @param fileName that has to be
   */
  public void ruleAdd(final String fileName) {
    LOGGER.error("Go into ADD for: " + fileName);
    final String ruleName = fileName.replace(YML, "");
    if (this.checkValidity(fileName)) {
      final ObjectMapper newmap = new ObjectMapper(new YAMLFactory());
      newmap.enable(SerializationFeature.INDENT_OUTPUT);
      /*
       * Change name of rule to filename, should the name of the rule is not equal to the filename.
       * Reason: rules in a rulebase need unambiguous names, otherwise the rule engine will throw
       * exceptions or ignore of the rules.
       */
      LOGGER.info("Adding : " + fileName);
      try {
        final RuleModel rule =
            newmap.readValue(new File(PATH_DIRECTORY + fileName), RuleModel.class);
        if (rule.getName().equalsIgnoreCase(ruleName)) {
          WatchRuleListService.ruleList.add(rule);
        } else {
          LOGGER.info("The filename " + fileName + " did not match with the name of the rule."
              + " Changed intern rule name to the filename.");
          rule.setName(ruleName);
          WatchRuleListService.ruleList.add(rule);
        }
      } catch (final JsonParseException | JsonMappingException e) {
        LOGGER.error("The Rule " + fileName
            + " seems to be invalid JSON. Please check the content of the file.");
      } catch (final IOException e) {
        LOGGER.error("File " + fileName + " does not exist.");
      }
    } else {
      LOGGER.error("Invalid rule: " + fileName);
    }
  }

  /**
   * Returns the actual rule list.
   *
   * @returns ruleList.
   */
  public ArrayList<RuleModel> getRules() {

    return WatchRuleListService.ruleList;
  }


  /**
   * Removes rules in the ruleList.
   *
   * @param ruleName of the rule that has to be eliminated.
   */
  public void ruleDel(final String ruleName) {
    final String name = ruleName.replace(YML, "");
    LOGGER.info("Remove : " + ruleName);
    WatchRuleListService.ruleList.removeIf(rule -> rule.getName().equals(name));
  }

  /**
   * Checks the validity of a rule.
   *
   * @param ruleName of the rule that has to be checked.
   * @returns true if its a valid rule, otherwise it will throw a exception
   */
  public boolean checkValidity(final String ruleName) {
    final MVELRuleFactory ruleFactory = new MVELRuleFactory(new YamlRuleDefinitionReader());
    try {

      ruleFactory.createRule(new FileReader(PATH_DIRECTORY + ruleName));

    } catch (final FileNotFoundException e) {
      LOGGER.info("Looks like the File does not exist.");
      return false;
    } catch (final Exception e) {
      LOGGER.info("Please check rule " + ruleName + ". Seems to be a invalid rule.");
      return false;
    }

    return true;
  }

  /**
   * Adds all rules found in the Rules folder.
   */
  public void checkActList() {
    final File folder = new File(DIRECTORY);
    final File[] listOfFiles = folder.listFiles();
    if (listOfFiles != null) {
      for (final File listOfFile : listOfFiles) {
        final String name = listOfFile.getName();
        this.ruleAdd(name);
      }
    }
  }

  @Override
  public void run() {

    WatchKey key;
    try {
      if ((key = this.watchService.take()) != null) {
        for (final WatchEvent<?> event : key.pollEvents()) {
          if (event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
            LOGGER.info("DELETE");
            this.ruleDel(event.context().toString());
          } else if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
            LOGGER.info("CREATE");
            this.ruleAdd(event.context().toString());
          } else if (event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
            LOGGER.info("MODIFY");
            this.ruleDel(event.context().toString());
            this.ruleAdd(event.context().toString());
          }

        }
        key.reset();
      }
    } catch (final InterruptedException e) {
      LOGGER.error("The watchservice does not work. Please restart.");
    }

  }

}
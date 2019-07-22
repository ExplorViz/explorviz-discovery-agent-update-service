package net.explorviz.extension.discovery_agent_update_service.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.TimerTask;
import org.jeasy.rules.mvel.MVELRuleFactory;
import org.jeasy.rules.support.YamlRuleDefinitionReader;
import net.explorviz.extension.discovery_agent_update_service.model.RuleModel;
import org.slf4j.LoggerFactory;	
/**
 * Watchs the rulefolder and updates the rulelist
 * @author enes
 *
 */
public class WatchRuleListService extends TimerTask {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(WatchService.class);
	private ArrayList<RuleModel> ruleList;
	private static final String pathToFolder = "Rules" + File.separator;
	private static WatchService watchService;
	private static Path path;
	
	public WatchRuleListService() {
		 new File("Rules").mkdir();
		 ruleList = new ArrayList<RuleModel>();
		try {
			watchService = FileSystems.getDefault().newWatchService();
			path = Paths.get("Rules");
			path.register(watchService, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY);
			getActList();
		} catch (IOException e) {
			LOGGER.error("Can't create folder Rules.");
			
		}
	}

	public void ruleAdd(String fileName) {
		final String ruleName = fileName.replace(".yml", "");
		if (checkValidity(fileName)) {
			ObjectMapper newmap = new ObjectMapper(new YAMLFactory());
			newmap.enable(SerializationFeature.INDENT_OUTPUT);
			/*
			 * Change name of rule to filename, should the name of the rule is not equal to the filename.
			 * Reason: rules in a rulebase need unambiguous names, otherwise the rule engine will throw exceptions.
			 */
		
			try {
				RuleModel rule = newmap.readValue(new File(pathToFolder + fileName), RuleModel.class);
				if(rule.getName().toLowerCase().equals(ruleName)) {
				ruleList.add(rule);
				}else {
					LOGGER.info("The filename " + fileName + " did not match with the name of the rule."
							+ " Changed rule name to the filename.");
					rule.setName(ruleName);
					ruleList.add(rule);
				}
			} catch (JsonParseException e) {
				LOGGER.error("The Rule " + fileName
						+ " seems to be invalid JSON. Please remove the file, check the content and try it again.");
			} catch (JsonMappingException e) {
				LOGGER.error("The Rule " + fileName
						+ " seems to be invalid JSON. Please remove the file, check the content and try it again.");
			} catch (IOException e) {
				LOGGER.error("File " + fileName + " does not exist.");
			}
		}
	}
/**
 * 
 * @returns ruleList. 
 */
	public ArrayList<RuleModel> getRules() {
		ArrayList<RuleModel> rules;
		synchronized (ruleList) {
			rules = ruleList;
		}

		return rules;
	}

	/**
	 * Removes rules in the ruleList.
	 * @param ruleName of the rule that has to be eliminated.
	 */
	public void ruleDel(String ruleName) {
		String name = ruleName.replace(".yml", "");
		ruleList.removeIf(rule -> rule.getName().equals(name));
	}

	/**
	 * Checks the validity of a rule. 
	 * @param ruleName of the rule that has to be checked.
	 * @returns true if its a valid rule, otherwise it will throw a exception
	 */
	public boolean checkValidity(String ruleName) {
		MVELRuleFactory ruleFactory = new MVELRuleFactory(new YamlRuleDefinitionReader());
		try {
			ruleFactory.createRule(new FileReader(pathToFolder + ruleName));
		} catch (FileNotFoundException e) {
			LOGGER.info("Looks like the File does not exist.");
			return false;

		} catch (Exception e) {
			LOGGER.info("Please check rule " + ruleName + ".");
			return false;
		}
		return true;
	}

	/**
	 * Adds all rules found in the Rules folder. 
	 */
	public void getActList() {
		File folder = new File("Rules");
		File[] listOfFiles = folder.listFiles();
		for (int i = 0; i < listOfFiles.length; i++) {
			String name = listOfFiles[i].getName();

			ruleAdd(name);

		}
	}

	@Override
	public void run() {
		WatchKey key;
		try {
			if ((key = watchService.take()) != null) {

				for (WatchEvent<?> event : key.pollEvents()) {
					if (event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
						ruleDel(event.context().toString());
					} else if (event.kind().equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						ruleAdd(event.context().toString());
					} else if (event.kind().equals(StandardWatchEventKinds.ENTRY_MODIFY)) {
						ruleDel(event.context().toString());
						ruleAdd(event.context().toString());
					}

				}
				key.reset();
			}
		} catch (InterruptedException e) {
		LOGGER.error("The watchservice does not work. Please restart.");
		}

	}

}

package org.selab.jcodelib.diffutil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jdt.core.dom.CompilationUnit;

import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.jdt.JdtTreeGenerator;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.Move;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.Update;
import file.FileIOManager;
import org.selab.jcodelib.element.CDChange;
import org.selab.jcodelib.element.GTAction;
import org.selab.jcodelib.util.CodeUtils;
import kr.ac.seoultech.selab.esscore.model.Script;
import kr.ac.seoultech.selab.esscore.util.GTScriptConverter;
import kr.ac.seoultech.selab.esscore.util.LASScriptConverter;


public class TreeDiff {

	public static List<SourceCodeChange> diffChangeDistiller(File left, File right){

		FileDistiller distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
		try {
			distiller.extractClassifiedSourceCodeChanges(left, right);
		} catch(Exception e) {
			/* An exception most likely indicates a bug in ChangeDistiller. Please file a
		       bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues and
		       attach the full stack trace along with the two files that you tried to distill. */
			System.err.println("Warning: error while change distilling. " + e.getMessage());
		}

		List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
		List<SourceCodeChange> filteredChanges = new ArrayList<>();
		if(changes != null) {
			for(SourceCodeChange change : changes) {
				if(!change.getChangedEntity().getType().isComment()){
					filteredChanges.add(change);
				}
			}
		}

		return filteredChanges;
	}

	public static List<CDChange> diffCDChanges(File left, File right) throws IOException {
		List<SourceCodeChange> changes = diffChangeDistiller(left, right);
		return convertSourceCodeChanges(changes);
	}

	public static List<CDChange> convertSourceCodeChanges(List<SourceCodeChange> changes) throws IOException {
		//Convert CDChange with new change type.
		List<CDChange> converted = new ArrayList<>();
		for(SourceCodeChange scc : changes) {
			SourceRange range = scc.getChangedEntity().getSourceRange();
			String changeType = scc.getChangeType().toString();
			if(scc instanceof Insert) {
				changeType = CDChange.INSERT;
			}else if(scc instanceof Delete) {
				changeType = CDChange.DELETE;
			}else if(scc instanceof Move) {
				changeType = CDChange.MOVE;
			}else if(scc instanceof Update) {
				changeType = CDChange.UPDATE;
			}
			converted.add(new CDChange(changeType, scc.getChangedEntity().getType().toString(),
					range.getStart(), range.getEnd()));
		}
		return converted;
	}

	public static List<com.github.gumtreediff.actions.model.Action> diffGumTree(File srcFile, File dstFile) throws Exception {
		//Updated for GumTree 3.0.0 version.
		com.github.gumtreediff.tree.Tree src = new JdtTreeGenerator().generateFrom().file(srcFile.getAbsolutePath()).getRoot();
		com.github.gumtreediff.tree.Tree dst = new JdtTreeGenerator().generateFrom().file(dstFile.getAbsolutePath()).getRoot();
		Matcher m = Matchers.getInstance().getMatcher();
		MappingStore mappings = m.match(src, dst);
		EditScriptGenerator g = new SimplifiedChawatheScriptGenerator();
		com.github.gumtreediff.actions.EditScript script = g.computeActions(mappings);
		List<com.github.gumtreediff.actions.model.Action> actions = script.asList();

		return actions;
	}

	public static DiffResult diffGumTree(String oldCode, String newCode) throws IOException {
		//Estimate runtime for Script Collection.
		long startTime = System.currentTimeMillis();
		com.github.gumtreediff.tree.Tree src = new JdtTreeGenerator().generateFrom().string(oldCode).getRoot();
		com.github.gumtreediff.tree.Tree dst = new JdtTreeGenerator().generateFrom().string(newCode).getRoot();
		Matcher m = Matchers.getInstance().getMatcher();
		MappingStore mappings = m.match(src, dst);
		EditScriptGenerator g = new SimplifiedChawatheScriptGenerator();
		com.github.gumtreediff.actions.EditScript script = g.computeActions(mappings);
		long endTime = System.currentTimeMillis();
		List<com.github.gumtreediff.actions.model.Action> actions = script.asList();
		//Convert to common script type. Not ignore ImportDecl. & combine tree edits.
		Script converted = GTScriptConverter.convert(actions, mappings, false, true);
		return new DiffResult(converted, endTime-startTime);
	}


	public static List<GTAction> groupGumTreeActions(File srcFile, File dstFile, List<com.github.gumtreediff.actions.model.Action> actions) {
		List<GTAction> gtActions = new ArrayList<>();
		try {
			CompilationUnit srcCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(srcFile));
			CompilationUnit dstCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(dstFile));
			//Group actions.
			while(actions.size() > 0){
				Action action = actions.get(0);
				GTAction gtAction = new GTAction(action, srcCu, dstCu);
				gtAction = attachActions(gtAction, actions, srcCu, dstCu);
				gtActions.add(gtAction);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return gtActions;
	}

	public static List<GTAction> diffGumTreeWithGrouping(File srcFile, File dstFile) throws Exception {
		List<GTAction> gtActions = new ArrayList<>();
		//Updated for GumTree 3.0.0 version.
		com.github.gumtreediff.tree.Tree src = new JdtTreeGenerator().generateFrom().file(srcFile.getAbsolutePath()).getRoot();
		com.github.gumtreediff.tree.Tree dst = new JdtTreeGenerator().generateFrom().file(dstFile.getAbsolutePath()).getRoot();
		Matcher m = Matchers.getInstance().getMatcher();
		MappingStore mappings = m.match(src, dst);
		EditScriptGenerator g = new SimplifiedChawatheScriptGenerator();
		com.github.gumtreediff.actions.EditScript script = g.computeActions(mappings);
		List<com.github.gumtreediff.actions.model.Action> actions = script.asList();
		CompilationUnit srcCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(srcFile));
		CompilationUnit dstCu = CodeUtils.getCompilationUnit(FileIOManager.getContent(dstFile));
		//Group actions.
		while(actions.size() > 0){
			Action action = actions.get(0);
			GTAction gtAction = new GTAction(action, srcCu, dstCu);
			gtAction = attachActions(gtAction, actions, srcCu, dstCu);
			gtActions.add(gtAction);
		}
		return gtActions;
	}

	private static GTAction attachActions(
			GTAction gtAction, List<Action> actions, CompilationUnit srcCu, CompilationUnit dstCu) {
		GTAction root = gtAction;

		//Bottom-up search to find a root action.
		GTAction parent;
		com.github.gumtreediff.tree.Tree parentNode;
		do {
			parent = null;
			parentNode = root.action.getNode().getParent();
			if (parentNode != null) {
				for (Action action : actions) {
					if (action.getNode() == parentNode
							&& GTAction.getActionType(action).equals(root.actionType)) {
						parent = new GTAction(action, srcCu, dstCu);
						break;
					}
				}
			}
			//Switch the root.
			if(parent != null){
				root = parent;
			}
		} while (parent != null);

		//Top-down search for children.
		List<GTAction> targetActions = new ArrayList<>();
		List<GTAction> attachedActions = new ArrayList<>();
		targetActions.add(root);
		actions.remove(root.action);
		do {
			targetActions.addAll(attachedActions);
			attachedActions.clear();
			//Find children of each target.
			for(GTAction target : targetActions){
				for (com.github.gumtreediff.tree.Tree child : target.action.getNode().getChildren()) {
					for (Action action : actions) {
						if (action.getNode() == child
								&& target.actionType.equals(GTAction.getActionType(action))) {
							GTAction gta = new GTAction(action, srcCu, dstCu);
							target.children.add(gta);
							attachedActions.add(gta);
							break;
						}
					}
				}
			}
			//Remove all attached actions.
			for(GTAction gta : attachedActions){
				actions.remove(gta.action);
			}
		} while (attachedActions.size() > 0 && actions.size() > 0);

		return root;
	}
}

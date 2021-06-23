/*
 * Cobertura - http://cobertura.sourceforge.net/
 *
 * Copyright (C) 2003 jcoverage ltd.
 * Copyright (C) 2005 Mark Doliner
 * Copyright (C) 2005 Jeremy Thomerson
 * Copyright (C) 2006 Jiri Mares
 * Copyright (C) 2008 Julian Gamble
 *
 * Cobertura is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * Cobertura is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cobertura; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package net.sourceforge.cobertura.reporting.xml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLOutputFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.cobertura.coveragedata.ClassData;
import net.sourceforge.cobertura.coveragedata.CoverageData;
import net.sourceforge.cobertura.coveragedata.JumpData;
import net.sourceforge.cobertura.coveragedata.LineData;
import net.sourceforge.cobertura.coveragedata.PackageData;
import net.sourceforge.cobertura.coveragedata.ProjectData;
import net.sourceforge.cobertura.coveragedata.SourceFileData;
import net.sourceforge.cobertura.coveragedata.SwitchData;
import net.sourceforge.cobertura.reporting.ComplexityCalculator;
import net.sourceforge.cobertura.reporting.xml.elements.Class;
import net.sourceforge.cobertura.reporting.xml.elements.Classes;
import net.sourceforge.cobertura.reporting.xml.elements.Condition;
import net.sourceforge.cobertura.reporting.xml.elements.Conditions;
import net.sourceforge.cobertura.reporting.xml.elements.Coverage;
import net.sourceforge.cobertura.reporting.xml.elements.Line;
import net.sourceforge.cobertura.reporting.xml.elements.Lines;
import net.sourceforge.cobertura.reporting.xml.elements.Method;
import net.sourceforge.cobertura.reporting.xml.elements.Methods;
import net.sourceforge.cobertura.reporting.xml.elements.Package;
import net.sourceforge.cobertura.reporting.xml.elements.Packages;
import net.sourceforge.cobertura.reporting.xml.elements.Source;
import net.sourceforge.cobertura.reporting.xml.elements.Sources;
import net.sourceforge.cobertura.util.FileFinder;
import net.sourceforge.cobertura.util.Header;
import net.sourceforge.cobertura.util.IOUtil;
import net.sourceforge.cobertura.util.StringUtil;

public class XMLReport {

	private static final Logger logger = LoggerFactory
			.getLogger(XMLReport.class);

	protected final static String coverageDTD = "coverage-04.dtd";

	private final ComplexityCalculator complexity;
	private int indent = 0;

	public XMLReport(ProjectData projectData, File destinationDir,
			FileFinder finder, ComplexityCalculator complexity) throws JAXBException, IOException{
		this.complexity = complexity;

		File file = new File(destinationDir, "coverage.xml");
		XMLOutputFactory factory = XMLOutputFactory.newFactory();

		try (PrintWriter printWriter = IOUtil.getPrintWriter(file)){
			double ccn = this.complexity.getCCNForProject(projectData);
			int numLinesCovered = projectData.getNumberOfCoveredLines();
			int numLinesValid = projectData.getNumberOfValidLines();
			int numBranchesCovered = projectData.getNumberOfCoveredBranches();
			int numBranchesValid = projectData.getNumberOfValidBranches();

			Coverage coverage = new Coverage();
			coverage.setLineRate(String.valueOf(projectData.getLineCoverageRate()));
			coverage.setBranchRate(String.valueOf(projectData.getBranchCoverageRate()));
			coverage.setLinesCovered(String.valueOf(numLinesCovered));
			coverage.setLinesValid(String.valueOf(numLinesValid));
			coverage.setBranchesCovered(String.valueOf(numBranchesCovered));
			coverage.setBranchesValid(String.valueOf(numBranchesValid));
			coverage.setComplexity(String.valueOf(ccn));
			coverage.setVersion(String.valueOf(Header.version()));
			coverage.setTimestamp(String.valueOf(new Date().getTime()));
			
			Sources sources = new Sources();
			for (Iterator it = finder.getSourceDirectoryList().iterator(); it
					.hasNext();) {
				String dir = (String) it.next();
				Source source = new Source();
				source.setContent(dir);
				sources.getSource().add(source);
			}
			coverage.setSources(sources);
			
			Packages packages = new Packages();
			Iterator it = projectData.getPackages().iterator();
			while (it.hasNext()) {
				PackageData packageData = (PackageData) it.next();
				Package pkg = new Package();
				packages.getPackage().add(pkg);
				pkg.setName(packageData.getName());
				pkg.setLineRate(String.valueOf(packageData.getLineCoverageRate()));
				pkg.setBranchRate(String.valueOf(packageData.getBranchCoverageRate()));
				pkg.setComplexity(String.valueOf(this.complexity.getCCNForPackage(packageData)));
				
				Classes classes = new Classes();
				pkg.setClasses(classes);
				
				Iterator it2 = packageData.getSourceFiles().iterator();
				while (it2.hasNext()) {
					SourceFileData sourceFileData = (SourceFileData) it2.next();
					Iterator it3 = sourceFileData.getClasses().iterator();
					while (it3.hasNext()) {
						ClassData classData = (ClassData) it3.next();
						dumpClasses(classes, classData);
					}
				}
				
			}

			coverage.setPackages(packages);
			
	        JAXBContext jaxbContext = JAXBContext.newInstance(Coverage.class);
	        Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
	        jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
	        jaxbMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
	        printWriter.write("<?xml version=\"1.0\"?>"
	        		  + "\n<!DOCTYPE coverage SYSTEM \"http://cobertura.sourceforge.net/xml/"
    				  + coverageDTD + "\">\n");
	        jaxbMarshaller.marshal(coverage, printWriter);

		} 
	}

	private void dumpClasses(Classes classes, ClassData classData) {
		Class clazz = new Class();
		classes.getClazz().add(clazz);
		
		clazz.setName(String.valueOf(classData.getName()));
		clazz.setFilename(String.valueOf(classData.getSourceFileName()));
		clazz.setLineRate(String.valueOf(classData.getLineCoverageRate()));
		clazz.setBranchRate(String.valueOf(classData.getBranchCoverageRate()));
		clazz.setComplexity(String.valueOf(this.complexity.getCCNForClass(classData)));
		
		Methods methods = new Methods();
		clazz.setMethods(methods);

		dumpMethods(classData, methods);
		
		SortedSet<CoverageData> lines = classData.getLines();
		Lines clazzLines = new Lines();
		clazz.setLines(clazzLines);
		dumpLines(lines, clazzLines);
	}

	private void dumpMethods(ClassData classData, Methods methods) {
		SortedSet<String> sortedMethods = new TreeSet<>();
		sortedMethods.addAll(classData.getMethodNamesAndDescriptors());
		Iterator<String> iter = sortedMethods.iterator();
		while (iter.hasNext()) {
			Method method = new Method();
			String nameAndSig = (String) iter.next();
			methods.getMethod().add(method);

			dumpMethod(classData, nameAndSig, method);

		}
	}

	private void dumpMethod(ClassData classData, String nameAndSig, Method method) {
		String name = nameAndSig.substring(0, nameAndSig.indexOf('('));
		String signature = nameAndSig.substring(nameAndSig.indexOf('('));
		double lineRate = classData.getLineCoverageRate(nameAndSig);
		double branchRate = classData.getBranchCoverageRate(nameAndSig);
		int methodComplexity = this.complexity.getCCNForMethod(classData, name, signature);
		
		method.setName(xmlEscape(name));
		method.setSignature(xmlEscape(signature));
		method.setLineRate(String.valueOf(lineRate));
		method.setBranchRate(String.valueOf(branchRate));
		method.setComplexity(String.valueOf(methodComplexity));

		increaseIndentation();
		Collection<CoverageData> lines = classData.getLines(nameAndSig);
		Lines methodLines = new Lines();
		method.setLines(methodLines);
		
		dumpLines(lines, methodLines);
	}

	private void dumpLines(Collection<CoverageData> lines, Lines xmlLines) {
		SortedSet<CoverageData> sortedLines = new TreeSet<>();
		sortedLines.addAll(lines);
		Iterator<CoverageData> iter4 = sortedLines.iterator();
		while (iter4.hasNext()) {
			LineData lineData = (LineData) iter4.next();
			Line line = new Line();
			xmlLines.getLine().add(line);
			dumpLine(lineData, line);
		}
	}

	private void dumpLine(LineData lineData, Line line) {
		int lineNumber = lineData.getLineNumber();
		long hitCount = lineData.getHits();
		boolean hasBranch = lineData.hasBranch();
		String conditionCoverage = lineData.getConditionCoverage();
		
		line.setNumber(String.valueOf(lineNumber));
		line.setBranch(String.valueOf(hasBranch));
		line.setHits(String.valueOf(hitCount));

		line.setConditionCoverage(String.valueOf(conditionCoverage));
		List<Conditions> conditionList = line.getConditions();
		dumpConditions(lineData, conditionList);
	}

	private void dumpConditions(LineData lineData, List<Conditions> conditionList) {
		Conditions conditions = new Conditions();
		conditionList.add(conditions);
		for (int i = 0; i < lineData.getConditionSize(); i++) {
			String coverage = lineData.getConditionCoverage(i);
			Object conditionData = lineData.getConditionData(i);
			Condition condition = new Condition();
			conditions.getCondition().add(condition);
			dumpCondition(coverage, conditionData, condition);
		}
	}

	private void dumpCondition(String coverage, Object conditionData, Condition condition) {
		if (conditionData instanceof JumpData) {
			JumpData jumpData = (JumpData) conditionData;
			condition.setCoverage(coverage);
			condition.setNumber(String.valueOf(jumpData.getConditionNumber()));
		} else {
			SwitchData switchData = (SwitchData) conditionData;
			condition.setCoverage(coverage);
			condition.setNumber(String.valueOf(switchData.getSwitchNumber()));
			condition.setType("switch");
		}
	}

	void increaseIndentation() {
		indent++;
	}

	void decreaseIndentation() {
		if (indent > 0)
			indent--;
	}

	private static String xmlEscape(String str) {
		str = StringUtil.replaceAll(str, "<", "&lt;");
		str = StringUtil.replaceAll(str, ">", "&gt;");
		return str;
	}


}

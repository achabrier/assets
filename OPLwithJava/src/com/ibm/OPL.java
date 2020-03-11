package com.ibm;

import ilog.concert.*;
import ilog.cp.*;
import ilog.opl.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class OPL
{
    private static final Logger LOGGER = Logger.getLogger(OPL.class.getName());

    static class MyData extends IloCustomOplDataSource
    {
        MyData(IloOplFactory oplF)
        {
            super(oplF);
        }

        public void csvToTuple(String element, String fileName, IloOplDataHandler handler, boolean isSet) {
            String content = getFileContent(fileName);
            List<String> lines = Arrays.asList(content.split("\n"));
            handler.startElement(element);
            if (isSet)
                handler.startSet();
            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.startsWith("//"))
                        continue;
                handler.startTuple();
                List<String> elts = Arrays.asList(line.split(","));
                for (Iterator<String> it2 = elts.iterator(); it2.hasNext(); ) {
                    String elt = it2.next();
                    if (elt.charAt(0) == '"')
                        elt = elt.substring(1);
                    if (elt.charAt(elt.length()-1) == '"')
                        elt = elt.substring(0, elt.length()-1);
                    try {
                        int i = Integer.parseInt(elt);
                        handler.addIntItem(i);
                    } catch (NumberFormatException nfe) {
                        try {
                            double d = Double.parseDouble(elt);
                            handler.addNumItem(d);
                        } catch (NumberFormatException nfe2) {
                            handler.addStringItem(elt);
                        }

                    }


                }
                handler.endTuple();
            }
            if (isSet)
                handler.endSet();
            handler.endElement();
        }
        public void csvToTuple(String element, String fileName, IloOplDataHandler handler) {
            csvToTuple(element, fileName, handler, false);
        }
        public void csvToTupleSet(String element, String fileName, IloOplDataHandler handler) {
            csvToTuple(element, fileName, handler, true);
        }
        public void emptyTupleSet(String element, IloOplDataHandler handler) {
            handler.startElement(element);
            handler.startSet();
            handler.endSet();
            handler.endElement();
        }
        public void csvToIndexedArray(String element, String fileName, IloOplDataHandler handler) {
            String content = getFileContent(fileName);
            List<String> lines = Arrays.asList(content.split("\n"));
            handler.startElement(element);
            handler.startIndexedArray();
            for (Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.startsWith("//"))
                    continue;

                List<String> elts = Arrays.asList(line.split(","));


                String elt = elts.get(0);
                if (elt.charAt(0) == '"')
                    elt = elt.substring(1);
                if (elt.charAt(elt.length()-1) == '"')
                    elt = elt.substring(0, elt.length()-1);
                try {
                    int i = Integer.parseInt(elt);
                    handler.setItemIntIndex(i);
                } catch (NumberFormatException nfe) {
                    try {
                        double d = Double.parseDouble(elt);
                        handler.setItemNumIndex(d);
                    } catch (NumberFormatException nfe2) {
                        handler.setItemStringIndex(elt);
                    }
                }

                elt = elts.get(1);
                if (elt.charAt(0) == '"')
                    elt = elt.substring(1);
                if (elt.charAt(elt.length()-1) == '"')
                    elt = elt.substring(0, elt.length()-1);
                try {
                    int i = Integer.parseInt(elt);
                    handler.addIntItem(i);
                } catch (NumberFormatException nfe) {
                    try {
                        double d = Double.parseDouble(elt);
                        handler.addNumItem(d);
                    } catch (NumberFormatException nfe2) {
                        handler.addStringItem(elt);
                    }

                }


            }
            handler.endIndexedArray();
            handler.endElement();
        }

        public void customRead()
        {
            IloOplDataHandler handler = getDataHandler();

            csvToTuple("parameters", "PARAMETERS.csv", handler);
            csvToTupleSet("weekdays", "WEEKDAYS.csv", handler);
            csvToTupleSet("periods", "PERIODS.csv", handler);
            csvToTupleSet("loads", "LOADS.csv", handler);
            csvToTupleSet("units", "UNITS.csv", handler);

            emptyTupleSet("mustTurnOffRules", handler);
            emptyTupleSet("maintenanceRules", handler);
            emptyTupleSet("showInUse", handler);
            emptyTupleSet("mustRunRules", handler);
            emptyTupleSet("maxProdRules", handler);
            emptyTupleSet("frozenInUse", handler);

            csvToIndexedArray("statusCodes", "STATUS_CODES.csv", handler);

            //
        }
    };

    static public void main(String[] args) throws Exception
    {
        int status = 127;
        try {
            IloOplFactory.setDebugMode(true);
            IloOplFactory oplF = new IloOplFactory();
            IloOplErrorHandler errHandler = oplF.createOplErrorHandler(System.out);
            IloOplModelSource modelSource=oplF.createOplModelSourceFromString(getModelText(),"UnitCommitment");
            IloOplSettings settings = oplF.createOplSettings(errHandler);
            IloOplModelDefinition def=oplF.createOplModelDefinition(modelSource,settings);
            IloCplex cplex = oplF.createCplex();
            IloOplModel opl=oplF.createOplModel(def,cplex);

            IloOplDataSource dataSource=new MyData(oplF);
            opl.addDataSource(dataSource);
            opl.generate();

            if ( cplex.solve() )
            {
                System.out.println("OBJECTIVE: " + opl.getCplex().getObjValue());
                opl.postProcess();
                opl.printSolution(System.out);
                status = 0;
            } else {
                System.out.println("No solution!");
                status = 1;
            }

            oplF.end();
        } catch (IloOplException ex) {
            System.err.println("### OPL exception: " + ex.getMessage());
            ex.printStackTrace();
            status = 2;
        } catch (IloException ex) {
            System.err.println("### CONCERT exception: " + ex.getMessage());
            ex.printStackTrace();
            status = 3;
        } catch (Exception ex) {
            System.err.println("### UNEXPECTED UNKNOWN ERROR ...");
            ex.printStackTrace();
            status = 4;
        }
    }

    public static String getFileContent(String inputFilename)  {
        String res = "";
        try {
            List<String> lines = Files.readAllLines(Paths.get(inputFilename));
            for (Iterator<String> it = lines.iterator(); it.hasNext();)
                res += it.next() + "\n";
        } catch (IOException e) {
            LOGGER.severe("Error getting file" + e.getStackTrace());
        }

        return res;
    }

    static String getModelText()
    {
        return getFileContent("UnitCommitment.mod");
    }
}


package com.ibm;

import ilog.concert.*;
import ilog.cp.*;
import ilog.opl.*;

public class carseq
{
    static class MyData extends IloCustomOplDataSource
    {
        MyData(IloOplFactory oplF)
        {
            super(oplF);
        }

        public void customRead()
        {
            int _nbConfs = 7;
            int _nbOptions = 5;

            IloOplDataHandler handler = getDataHandler();
            handler.startElement("nbConfs");
            handler.addIntItem(_nbConfs);
            handler.endElement();
            handler.startElement("nbOptions");
            handler.addIntItem(_nbOptions);
            handler.endElement();

            int _demand[] = {5, 5, 10, 10, 10, 10, 5};
            handler.startElement("demand");
            handler.startArray();
            for (int i= 0; i< _nbConfs; i++)
                handler.addIntItem(_demand[i]);
            handler.endArray();
            handler.endElement();

            int _option[][] = {{1, 0, 0, 0, 1, 1, 0},
                    {0, 0, 1, 1, 0, 1, 0},
                    {1, 0, 0, 0, 1, 0, 0},
                    {1, 1, 0, 1, 0, 0, 0},
                    {0, 0, 1, 0, 0, 0, 0}};
            handler.startElement("option");
            handler.startArray();
            for (int i = 0 ; i< _nbOptions ; i++) {
                handler.startArray();
                for (int j = 0 ; j<_nbConfs ; j++)
                    handler.addIntItem(_option[i][j]);
                handler.endArray();
            }
            handler.endArray();
            handler.endElement();

            int _capacity[][] = {{1, 2}, {2, 3}, {1, 3}, {2, 5}, {1, 5}};
            handler.startElement("capacity");
            handler.startArray();
            for (int i = 0; i<_nbOptions;i++) {
                handler.startTuple();
                for (int j= 0; j<=1;j++)
                    handler.addIntItem(_capacity[i][j]);
                handler.endTuple();
            }
            handler.endArray();
            handler.endElement();
        }
    };

    static public void main(String[] args) throws Exception
    {
        int status = 127;
        try {
            IloOplFactory.setDebugMode(true);
            IloOplFactory oplF = new IloOplFactory();
            IloOplErrorHandler errHandler = oplF.createOplErrorHandler(System.out);
            IloOplModelSource modelSource=oplF.createOplModelSourceFromString(getModelText(),"carseq");
            IloOplSettings settings = oplF.createOplSettings(errHandler);
            IloOplModelDefinition def=oplF.createOplModelDefinition(modelSource,settings);
            IloCP cp = oplF.createCP();
            IloOplModel opl=oplF.createOplModel(def,cp);

            IloOplDataSource dataSource=new MyData(oplF);
            opl.addDataSource(dataSource);
            opl.generate();

            if ( cp.solve() )
            {
                System.out.println("OBJECTIVE: " + opl.getCP().getObjValue());
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

    static String getModelText()
    {
        String model="using CP;";
        model+="int nbConfs   = ...;";
        model+="int nbOptions = ...;";
        model+="range Confs = 1..nbConfs;";
        model+="range Options = 1..nbOptions;";
        model+="int demand[Confs] = ...;";
        model+="tuple CapacitatedWindow {";
        model+="  int l;";
        model+="  int u;";
        model+="};";
        model+="CapacitatedWindow capacity[Options] = ...; ";
        model+="range AllConfs = 0..nbConfs;";
        model+="int nbCars = sum (c in Confs) demand[c];";
        model+="int nbSlots = ftoi(floor(nbCars * 1.1 + 5)); ";
        model+="int nbBlanks = nbSlots - nbCars;";
        model+="range Slots = 1..nbSlots;";
        model+="int option[Options,Confs] = ...; ";
        model+="int allOptions[o in Options, c in AllConfs] = (c == 0) ? 0 : option[o][c];";
        model+="dvar int slot[Slots] in AllConfs;";
        model+="dvar int lastSlot in nbCars..nbSlots;";

        model+="minimize lastSlot - nbCars; ";
        model+="subject to {";
        model+="  count(slot, 0) == nbBlanks;";
        model+="  forall (c in Confs)";
        model+="    count(slot, c) == demand[c];";
        model+="  forall(o in Options, s in Slots : s + capacity[o].u - 1 <= nbSlots)";
        model+="    sum(j in s .. s + capacity[o].u - 1) allOptions[o][slot[j]] <= capacity[o].l;";
        model+="  forall (s in nbCars + 1 .. nbSlots)";
        model+="    (s > lastSlot) => slot[s] == 0;";
        model+="}";
        return model;
    }
}


package simulation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import events.EventManager;
import events.Pauser;
import events.TickPause;
import gui.MigFrame;

/**
 * Migrationscape version 2.2
 * A version of the Schelling segregation model with adaptive tolerance.
 * Author: Linda Urselmans
 * University of Essex
 *  **/

public class SimController extends Observable implements Runnable, Observer
{

	static Simulation simulation;
	Pauser pauser = new Pauser();
	static int tempLine[] = { -1, -1, -1, -1, -1, -1 };
	static int NUM_SIMULATIONS = -1;
	static int TICKS = -1;
	static int START_DENSITY = -1;
	static int FINAL_DENSITY = -1;
	static int INFLUX = -1; // 0 = OFF
	static int INFLUX_PC = -1;
	static int POPULATING_RULE = -1;
	static int T1 = -1;
	static int T2 = -1;
	static int W = -1;
	static int NB = 3;
	static double M = -1;
	static ArrayList<int[]> setupList;
	static int[] modi;
	static int THRESHOLD_RULE; // 0 normal, 1 uniform, 2 F1F2, 3 F0
	MigFrame frame;
	private Thread game;
	static EventManager event;
	static SimController controller;
	boolean isPaused = false;
	static boolean isUIOFF = true;
	static int[] simPara;
	static int[] mapPara;
	static int[] influxPara;
	static int[] addedPara;
	static int sd1 = 0;
	static int sd2 = 0;
	private static int tick;

	public SimController(int[] simPara, int[] mapPara, int[] influxPara, int[] addedPara)
	{
		event = new EventManager();
		NUM_SIMULATIONS = simPara[0];
		TICKS = simPara[1];
		START_DENSITY = mapPara[0];
		FINAL_DENSITY = mapPara[1];
		INFLUX = influxPara[0];
		INFLUX_PC = influxPara[1];
		POPULATING_RULE = 0; // 0=random, 1=segregation
		W = addedPara[0];
		M = addedPara[1];
		modi = new int[] { START_DENSITY, INFLUX, INFLUX_PC, THRESHOLD_RULE };
	}

	public static void runRule(int rule) throws IOException
	{
		
		int[] tdensityoptions = {80, 90};
		int[] shareoptions = {75, 50};
		int[] infcoptions = {0, 1, 4, 15}; // 0 means influx off

		int numrep = 64; 
		int numtick = 20000;
		int m = 1;
		int w = 50;

		int runCount = 0;

		for (int td : tdensityoptions) {
		    for (int sd : shareoptions) {
		        for (int infc : infcoptions) {
		            // Repeat each combination 4 times
		            for (int rep = 0; rep < 4; rep++) {
		                int influx, influxcount;

		                if (infc == 0) {
		                    influx = 0;
		                    influxcount = 0;
		                } else {
		                    influx = 1;
		                    influxcount = infc;
		                }

		                simPara = new int[] { numrep, numtick }; // numSim, numTicks
		                mapPara = new int[] { sd, td }; // starting-density, final-density
		                influxPara = new int[] { influx, influxcount }; // influxONOFF, influxcount
		                addedPara = new int[] { w, m }; // w, m

		                tick = runCount++;
		                long startTime = System.currentTimeMillis();

		                SimController controller = new SimController(simPara, mapPara, influxPara, addedPara);
		                event.addObserver(controller);

		                String[] inf = { "OFF", "ON" };
		                Date now = new Date();
		                String startstr = (runCount) + " sim started at " + now + ". Params: "
		                        + "GR" + mapPara[0] + "/FD" + mapPara[1]
		                        + "/I-" + inf[influx] + "/" + influxPara[1]
		                        + "/M" + m + "/W" + w;
		                System.out.println(startstr);

		                controller.run();

		                long estimatedTime = System.currentTimeMillis() - startTime;
		                long minutes = (estimatedTime / 1000) / 60;
		                int seconds = (int) (estimatedTime / 1000) % 60;
		                now = new Date();

		                System.out.println(runCount + " ended at " + now
		                        + ". Duration: " + minutes + "m" + seconds + "s (" + estimatedTime + " ms)");
		            }
		        }
		    }
		}
		System.exit(0);
	}

	public static void main(String[] args) throws IOException
	{
		isUIOFF = true; //true=UI is off; false = UI is on
		int runrule = 1; //0=manual, 1=random
		//int simn = 60; //number of simulations
		runRule(runrule);
	}

	@Override
	public void run()
	{
		Random prng = new Random();
		try
		{
			simulation = new Simulation(simPara, mapPara, influxPara, addedPara, prng, pauser);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		game = new Thread(simulation);
		simulation.getWorld().addObserver(simulation);
		simulation.addObserver(simulation.getWorld());

		if (isUIOFF)
		{
			game.start();
			simulation.initAppeal();
			simulation.run(tick);

		}

		else
		{
			MigFrame frame = new MigFrame(simulation.getWorld(), event, modi);
			frame.open();
			simulation.addObserver(frame.getUserControls());
			simulation.addObserver(frame.getParameters());
			game.start();
			simulation.initAppeal();
			simulation.run(tick);
			frame.dispose();
		}
	}


	@Override
	public void update(Observable o, Object arg)
	{
		if (arg instanceof TickPause)
		{
			setPause();
		}
	}

	public void fireEvent(Object event)
	{
		setChanged();
		notifyObservers(event);
	}

	public void setPause()
	{
		if (isPaused == true)
		{
			pauser.resume();
			isPaused = false;
		}
		else
		{
			pauser.pause();
			isPaused = true;
		}
	}

	public static int randInt(int min, int max)
	{

		Random rand = new Random();
		// nextInt is normally exclusive of the top value, so add 1 to make it inclusive
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}
}

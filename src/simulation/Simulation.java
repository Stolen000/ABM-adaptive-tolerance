package simulation;

import java.awt.AWTException;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import javax.imageio.ImageIO;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import com.opencsv.CSVWriter;
import events.AgentMovedEvent;
import events.Pauser;
import events.SimulationFinishedEvent;
import events.SimulationStartedEvent;
import events.TickEnd;
import events.TickStart;
import gui.Grid;
import migscape.Agent;
import migscape.Tile;
import migscape.World;

/**
 * Migrationscape version 2.2
 * A version of the Schelling segregation model with adaptive tolerance.
 * Author: Linda Urselmans
 * University of Essex
 *  **/
public class Simulation extends Observable implements Observer, Runnable
{
	double[] data = { 0, 0, 0, 0 }; // same, different, empty, happiness
	int sizeX = -1; // world size X axis
	int sizeY = -1; // world size Y axis
	int numBlues = -1; // total number of Blues
	int numGreens = -1; // total number of Greens
	int g; // start density and green ratio
	int fd; //final density
	double meanBlue = 0;
	int numAgents; // total num of agents, blue + green
	int agentsTarget;
	int agentsStart;
	int agentsGreen;
	int agentsBlue;
	double mC = 0; // morans I of colour
	double mT = 0; // morans I of tolerance
	int maxTicks = 0; // total number of ticks (rounds)
	int agentPercent = -1; // percentage of the world covered by agents
	int noOfSims = -1; // number of simulations
	int w = -1; // max number of tiles that an agent will consider.
	double m = -1; // tolerance in/decrement
	Double globalMoveCounter;
	double globalHappiness = 0.0;
	double globalAffordability = 0.0;
	double globalTolerance = 0.0;


	double averageTB = 0.0;
	double averageTG = 0.0;
	int[] errorcounter = { 0, 0 }; // blue agent error. green agent error
	int[] totalNBHforGreens = { 0, 0, 0 };
	int[] totalNBHforBlues = { 0, 0, 0 };
	CSVWriter writer;
	int prefcMcounter = 0;
	String csvname = "";
	ArrayList<Agent> allAgents; // list of all agents		
	ArrayList<Tile> emptyTiles;
	World world;
	Random prng;
	Grid grid;
	Integer tick;
	Pauser pauser;
	int[] population = { 0, 0 }; // array storing numbers of blues [0] and greens[1]
	int[][] csvAppealValues;
	int totalInfluxMax;
	DecimalFormat df4 = new DecimalFormat("#.####");
	DecimalFormat df2 = new DecimalFormat("#.##");
	DecimalFormat df0 = new DecimalFormat("#");
	boolean utilityOn = true;
	int changedMind = 0;
	int influx = -1;
	private static final int SAMPLING_INTERVAL = 10;
	int fluxcounter = 0;
	private int NUM_FLUXES = -2;
	private ArrayList<int[]> TICKS_OF_FLUX = new ArrayList<int[]>();
	int current_size = -1;
	int maxfluxsize;
	int setupMode = -999;
	boolean csvHeaderWritten = false;
	
	
	
    final double W_COLOR_RICH = 1; // weight for race similarity
    final double W_CLASS_RICH = 0; // weight for class similarity
    final double W_COLOR_MEDIUM = 1; // weight for race similarity
    final double W_CLASS_MEDIUM = 0; // weight for class similarity
    final double W_COLOR_POOR = 1; // weight for race similarity
    final double W_CLASS_POOR = 0; // weight for class similarity
    
    
	final double AFFORDABILITY = 0.3; // threshold for tile affordability
	final double ALPHA_CLASS = 0.5;  // higher => harsher penalty for class distance
	
	
	// ---- cost formula params ----
	double BASELINE_COST   = 50;   // baseline cost level
	double INCOME_SENSIVITY = 1;   // sensitivity to neighborhood income m(p)
	double LOW_DENSITY_SENSIVITY = 0;   // vacancy discount strength (subtracts cost)
	double HIGH_DENSITY_SENSIVITY  = 0;   // density surcharge strength (adds cost)

	// smoothing to avoid jitter
	double ALPHA = 0.3;  // cost_t = (1-ALPHA)*cost_{t-1} + ALPHA*cost_hat;  0=no update, 1=no smoothing

	// neighborhood window (Moore radius); 3 ≈ 7x7
	final int RENT_RADIUS = 3;
    final int SOCIAL_CLASS_RADIUS = 2;      // Moore radius for class neighborhood

	// income normalization
	final static double nativeIncomeMedian = 500.0;
	final static double migrantIncomeMedian = 500.0;
	final static double nativeGINI = 0.35;
	final static double migrantGINI = 0.35 ;
	double incomeCapForRent    = 3.0 * nativeIncomeMedian; // clamp scale
	// Optional: choose whether to include the center cell in the neighborhood stats for income
	private static final boolean INCLUDE_CENTER = true;
	// Optional: distance weighting (Chebyshev). If false, all neighbors weight=1.
	private static final boolean WEIGHT_BY_DISTANCE = true;
	private static final double RENT_FLOOR = 50;       // never below this
	private static final double RENT_CEIL  = 3* nativeIncomeMedian; // or a number if you want a cap
	
	private enum Attr { COLOUR, TOLERANCE, CLASS}

	// ======= Class-level constants =======
	private static final int RACES = 2;     // 0=Blue, 1=Green
	private static final int CLASSES = 3;   // 0,1,2  Social Classes -> Poor, Medium, Rich

	// ======= NEW: Global accumulators (private) =======
	private final int[][] _countsByRaceClass      = new int[RACES][CLASSES];
	private final int[][] _happyCountsByRaceClass = new int[RACES][CLASSES];
	private final double[][] _tolSumByRaceClass   = new double[RACES][CLASSES];
	private final double[][] _affordSumByRaceClass= new double[RACES][CLASSES];

	private final int[] _countsByRace   = new int[RACES];
	private final int[] _happyByRace    = new int[RACES];
	private final double[] _tolSumByRace   = new double[RACES];
	private final double[] _affordSumByRace= new double[RACES];

	private final int[] _countsByClass   = new int[CLASSES];
	private final int[] _happyByClass    = new int[CLASSES];
	private final double[] _tolSumByClass   = new double[CLASSES];
	private final double[] _affordSumByClass= new double[CLASSES];

	// ======= NEW: Global published metrics (public) =======
	// Per (race × class)
	public final double[][] happyRateByRaceClass  = new double[RACES][CLASSES]; // [0,1]
	public final double[][] tolMeanByRaceClass    = new double[RACES][CLASSES]; // [0,1]
	public final int[][]    countsByRaceClass     = new int[RACES][CLASSES];    // counts

	// Collapsed by race (over classes)
	public final double[] happyRateByRace   = new double[RACES]; // [0,1]
	public final double[] tolMeanByRace     = new double[RACES]; // [0,1]
	public final int[]    countsByRace      = new int[RACES];    // counts

	// Collapsed by class (over races)
	public final double[] happyRateByClass  = new double[CLASSES]; // [0,1]
	public final double[] tolMeanByClass    = new double[CLASSES]; // [0,1]
	public final int[]    countsByClass     = new int[CLASSES];    // counts


	private int affordBelowCountAll;
	private final int[] affordBelowByRace  = new int[RACES];     // [blue, green]
	private final int[] affordBelowByClass = new int[CLASSES];   // [C0,C1,C2]
	
	
	
	// -------- per-tick counters, split by race (0=Green, 1=Blue) and class (0,1,2) --------
	private int[] failedMoveAttemptsByRace   = new int[2];
	private int[] unhappyCheckedByRace       = new int[2];
	private int[] totalChoicesByRace         = new int[2];

	private int[] failedMoveAttemptsByClass  = new int[3];
	private int[] unhappyCheckedByClass      = new int[3];
	private int[] totalChoicesByClass        = new int[3];

	// derived per-tick averages (exposed if you want to read them elsewhere)
	public double[] avgChoicesPerUnhappyByRace  = new double[2];
	public double[] avgChoicesPerUnhappyByClass = new double[3];

	// correlations: global, by race, by class
	public double  priceClassCorr            = 0.0;   // (you already had this name in the CSV row)
	public double priceRaceCorr = 0.0;
	
	
	
	private double[][] clusterStatsRace  = new double[2][3];
	private double[][] clusterStatsClass = new double[3][3];




	List<int[]> floodFillResults;
	List<int[]> influxPositions;
	List<String[]> values = new ArrayList<String[]>();
	List<Integer> ticksHappinessList = new ArrayList<Integer>();
	int[] thresholdDistribution = { 0, 0, 0, 0, 0 };
	String currentDirectory = "";

	
	
	/**
	 * This is the constructor. 
	 */
	public Simulation(int[] simPara, int[] mapPara, int[] influxPara, int[] addedPara, Random prngFromController, Pauser pauser) throws IOException
	{
		// non-parameters
		sizeX = 50; // change the grid size here.
		sizeY = 50;

		// parameters
		w = addedPara[0];
		m = addedPara[1]; //divide by 10 or 100 to decrease m (rate of change of tolerance)
		System.out.println(m);
		g = mapPara[0];
		fd = mapPara[1];
		int[] agents = calculateNatAndMigAgents(influxPara[0], g, fd); //returns: target, start, green, blue
		agentsTarget = agents[0]; // maximum number of agents that simulation has to reach
		agentsStart = agents[1];
		agentsGreen = agents[2];
		agentsBlue = agents[3];

		noOfSims = simPara[0];
		maxTicks = simPara[1];
		numAgents = agentsStart; 
		maxfluxsize = agentsTarget - agentsStart; // should be 1200 if targetagents is 2450 and numagents 1250
		totalInfluxMax = (sizeX * sizeY) - numAgents;

		if (influxPara[0] == 1) // if influx is turned on, set the influx pc of population
		{
			setupMode = 0;
			influx = influxPara[0];
			NUM_FLUXES = influxPara[1];
			TICKS_OF_FLUX = calculateInfluxWHEN(NUM_FLUXES);

			print("<<Simulation.constructor>> the ticks at which to flux are: ");
			for (int i = 0; i < TICKS_OF_FLUX.size(); i++)
			{
				System.out.print(TICKS_OF_FLUX.get(i)[0] + ", ");
			}
			print(" totalling " + TICKS_OF_FLUX.size() + " influxes.");

		}
		else
		{
			setupMode = 2;
			influx = influxPara[0];
		}

		prng = prngFromController;
		this.pauser = pauser;

		floodFillResults = new ArrayList<int[]>();
		influxPositions = new ArrayList<int[]>();
		if (sizeX * sizeY < numAgents)
		{
			print("<<Simulation.constructor>>There are too many agents for the size of the grid. Number of Agents: " + numAgents + ", Number of tiles: " + sizeX * sizeY);
			System.exit(-1);
		}
		allAgents = new ArrayList<Agent>();
		emptyTiles = new ArrayList<Tile>();
		world = new World(sizeX, sizeY);
		populateMap();
		updateRent();
		grid = new Grid(world, false);
		collectEmptyTiles();
		globalMoveCounter = 0.0;
		collectPopulationData();
	}

	/**
	 * This is the ratio-calculation method from MigScape1.1, adapted to
	 * MigScape2.1. The difference is that tolerance levels are
	 * adaptive. 
	 * @param g
	 *            = ratio of greens in percentage of presumed 100% pop. So g =
	 *            30 means, at the end of the simulation, greens should
	 *            constitute 30% of the population.
	 * @param fd
	 *            = final density. Final density is given in % (0-100) and
	 *            converted into actual agent numbers. We calculate the number
	 *            of greens using fd and g, and then simply subtract finalagents
	 *            - startingagents to get the migrating agents, mg_agents.
	 */
	public int[] calculateNatAndMigAgents(int isInfluxOn, int g, int fd)
	{
		//print("<calculateNatAndMigAgents> g/fd are " + g + "/" + fd + ".");
		int[] agentnumbers = new int[4]; // contains fd_agents, sd_agents, gr_agents, bl_agents
		int onePC_agents = (sizeX * sizeY) / 100;// 1% = 25 agents.
		int fd_agents = 0;
		int sd_agents = 0;
		int gr_agents = 0;
		int bl_agents = 0;

		if (isInfluxOn == 1)
		{
			fd_agents = fd * onePC_agents; // i.e. 98 * 25 = 2450
			sd_agents = (fd_agents * g) / 100; // i.e. (2450 * 30)/100 = 735
			gr_agents = sd_agents;
			bl_agents = fd_agents - gr_agents; // i.e 2450 - 735 = 1715
		}

		else
		{
			fd_agents = fd * onePC_agents; // i.e. 98 * 25 = 2450
			gr_agents = (fd_agents * g) / 100;
			bl_agents = fd_agents - gr_agents;
			sd_agents = fd_agents;
			//this.g = fd; //green ratio set to final density to ensure correct csv name
		}

		agentnumbers[0] = fd_agents;
		agentnumbers[1] = sd_agents;
		agentnumbers[2] = gr_agents;
		agentnumbers[3] = bl_agents;

		//print("<calculateNatAndMigAgents> Influx is " + isInfluxOn + ".");
		//print("<calculateNatAndMigAgents> fd/sd/gr/bl: " + fd_agents + "/" + sd_agents + "/" + gr_agents + "/" + bl_agents + ".");
		return agentnumbers;
	}

	
	
	

	/**
	 * Method to add the agents to the map.
	 * In MigScape2.1, we take a ratio argument for greens and a final density argument.
	 * 
	 * There are 2 major differences to account for:
	 * 1. Is influx on or not (determined by setupMode 0 and 2 respectively).
	 * 2. Are tolerance levels fixed or randomised (determined by TR0-1 and 2-3 respectively).
	 * 
	 * When Influx is OFF, the final density is also the starting density, and the number of greens is calculated from the final density.
	 * When Influx is ON, the number of greens is also the starting density, and the gap will be filled by blues. 
	 * Example:
	 * GR 90, FD 70
	 * INF OFF: SD = FD; Greens are 90% OF the 70%
	 * INF ON: SD = Greens; i.e. 90% of what will be 70%, the remaining 10% will migrate later.
	 **/
	public void populateMap() throws IOException
	{
		if (setupMode == 2)//influx OFF
		{

			for (int i = 0; i < agentsTarget; i++)
			{
				int x = prng.nextInt(sizeX);
				int y = prng.nextInt(sizeY);
				boolean isBlue = false; // default is, agents are green
				double newAgentTolerance = generateThreshold(); // uniform
				if (i > agentsGreen)
				{
					isBlue = true; //under SC, the number of greens and blues is determined at the start
				}
				int money = generateAgentIncome(nativeIncomeMedian, nativeGINI, System.nanoTime());
				int socialClass = classifyIncome(money, nativeIncomeMedian);
				allAgents.add(new Agent(x, y, isBlue, newAgentTolerance, 2, money, socialClass, generateThreshold())); ////Add new param richness
				Agent agentPara = allAgents.get(i);
				

				Tile currTile = world.getTile(agentPara.posX, agentPara.posY);	////Add new param for renda

				if (currTile.hasAgent() == true) // if the tile with the same position as the agent already has an agent, then ...
				{
					// print("<<populateMap>> Tile with Position " + positionPara.getX() + ", " + positionPara.getY() + " already exists.");
					boolean hasFoundTile = false;
					while (hasFoundTile == false)
					{
						x = prng.nextInt(world.getSizeX()); // generate x-value between 0 and 50 if world.SizeX=50
						y = prng.nextInt(world.getSizeY()); // generate y-value between 0 and 50 if world.SizeY=50

						Tile findRightTile = world.getTile(x, y); // random tile
						if (findRightTile.hasAgent() == false) // check if it has agent: has not? then
						{
							allAgents.get(i).setPosition(x, y); // give agent that position
							hasFoundTile = true;
						}
					}
				}
				world.placeAgentOnTile(agentPara.posX, agentPara.posY, isBlue, agentPara.isHappy, newAgentTolerance, agentPara.richness, agentPara.socialClass);
				
			}

		}
		else if (setupMode == 0) //influx ON
		{
			for (int i = 0; i < agentsStart; i++)
			{
				int x = prng.nextInt(sizeX);
				int y = prng.nextInt(sizeY);
				boolean isBlue = false; // default is, agents are green
				double newagenttolerance = generateThreshold(); // uniform
				int money = generateAgentIncome(nativeIncomeMedian, nativeGINI, System.nanoTime());
				int socialClass = classifyIncome(money, nativeIncomeMedian);
				allAgents.add(new Agent(x, y, isBlue, newagenttolerance, 2, money, socialClass, generateThreshold()));
				Agent agentPara = allAgents.get(i);

				Tile currTile = world.getTile(agentPara.posX, agentPara.posY);

				if (currTile.hasAgent() == true) // if the tile with the same position as the agent already has an agent, then ...
				{
					// print("<<populateMap>> Tile with Position " + positionPara.getX() + ", " + positionPara.getY() + " already exists.");
					boolean hasFoundTile = false;
					while (hasFoundTile == false)
					{
						x = prng.nextInt(world.getSizeX()); // generate x-value between 0 and 50 if world.SizeX=50
						y = prng.nextInt(world.getSizeY()); // generate y-value between 0 and 50 if world.SizeY=50

						Tile findRightTile = world.getTile(x, y); // random tile
						if (findRightTile.hasAgent() == false) // check if it has agent: has not? then
						{
							allAgents.get(i).setPosition(x, y); // give agent that position
							hasFoundTile = true;
						}
					}
				}
				world.placeAgentOnTile(agentPara.posX, agentPara.posY, isBlue, agentPara.isHappy, newagenttolerance, agentPara.richness, agentPara.socialClass);
				
			}
		}

		//print("<populateMap> setupMode " + setupMode + ". targetAgents " + agentsTarget + ", startAgents " + agentsStart + ". g is " + g + ", greens is "
		//		+ df0.format(agentsTarget * (g / 100.00)));
	}

	public int countAgentsOnMap()
	{
		int agentcount = 0;
		int blucount = 0;
		int grecount = 0;
		int emptycount = 0;
		for (int x1 = 0; x1 < world.getSizeX(); x1++)
			for (int y1 = 0; y1 < world.getSizeY(); y1++)
			{
				Tile curr = world.getTile(x1, y1);
				if (curr.hasAgent())
				{
					agentcount++;
					if (curr.isAgentBlue())
					{
						blucount++;
					}
					else if (!curr.isAgentBlue())
					{
						grecount++;
					}
				}
				else
					emptycount++;
			}
		return emptycount;
	}
	
	
	private int[][] getNeighbourPositionsMoore(int[] currPos, int radius) {
	    int x0 = currPos[0], y0 = currPos[1];
	    int side = 2 * radius + 1;
	    int total = side * side - 1; // exclude center
	    int[][] neighbours = new int[total][2];

	    int idx = 0;
	    for (int dy = -radius; dy <= radius; dy++) {
	        for (int dx = -radius; dx <= radius; dx++) {
	            if (dx == 0 && dy == 0) continue; // skip center
	            int nx = wrap(x0 + dx, sizeX);
	            int ny = wrap(y0 + dy, sizeY);
	            neighbours[idx][0] = nx;
	            neighbours[idx][1] = ny;
	            idx++;
	        }
	    }
	    return neighbours;
	}
	

	/**
	 * checks the surrounding X neighbourhood for a given tile.<br>
	 * explanation: <br>
	 * first retrieve the target tile's neighbours (int[X][2])<br>
	 * Then loop through those neighbours, counting agents and empty tiles. that
	 * gets stored:<br>
	 * output: int [3] <br>
	 * [0] number of blues<br>
	 * [1] number of greens<br>
	 * [2] number of empty tiles
	 * 
	 * @return int[3] bluec greenc emptyc
	 **/
	public int[] checkNBH(int[] targetTile)
	{
		Tile tile = world.getTile(targetTile[0], targetTile[1]);
		int[] tileNBHInfo = new int[3]; // store no. of blue, green and empty
		int[][] tileNeighbourPositions = null;
		int bluec = 0;
		int greenc = 0;
		int emptyc = 0;

		tileNeighbourPositions = getNeighbourPositionsMoore(tile.getPosition(), 2);

		// loop through the X neighbours
		for (int i = 0; i < tileNeighbourPositions.length; i++)
		{
			Tile currT = world.getTile(tileNeighbourPositions[i][0], tileNeighbourPositions[i][1]);
			if (currT.hasAgent())
			{
				if (currT.isAgentBlue())
				{
					bluec++;
				}
				else
				{
					greenc++;
				}
			}
			else
			{
				emptyc++;
			}

		}
		tileNBHInfo[0] = bluec;
		tileNBHInfo[1] = greenc;
		tileNBHInfo[2] = emptyc;
		return tileNBHInfo;
	}

	
	
	
	/**
	 * Main method for agents determining their happiness.<br>
	 * The method takes the previously collected moore NBH info and loops
	 * through it, counting for differents and sames (changing depending on
	 * whether agent is blue or green)<br>
	 * the count gets compared to the threshold and the happiness is set.
	 */
	public void updateHappiness(int agentIndex, int[] surroundingInfo) 
	{
	    int amHappy = 2;

	    Agent currAgent = allAgents.get(agentIndex);
	    double threshold = currAgent.getThreshold();  // percent, e.g. 95
	    
	    double emptyCount = surroundingInfo[2];

	    // --- color similarity (as before) ---
	    double sameColor, diffColor;
	    if (currAgent.isBlue) {
	        sameColor = surroundingInfo[0]; // blues around blue
	        diffColor = surroundingInfo[1]; // greens around blue
	    } else {
	        sameColor = surroundingInfo[1]; // greens around green
	        diffColor = surroundingInfo[0]; // blues around green
	    }
	    double totalColor = sameColor + diffColor;
	    double max = totalColor + emptyCount;
	    double colorRatio = sameColor / totalColor;

	    // Isolation rule: no neighbors → unhappy
	    if (emptyCount == max) {
	        amHappy = 0;
	    } else {
	        // --- class similarity: graded by distance (poor↔rich worse than poor↔medium) ---
	        int myClass = currAgent.getSocialClass(); // 0,1,2  (0=poor,1=middle,2=rich)

	        // Tunables

	        double clsSameW = 0.0;  // weighted "same" mass
	        double clsDiffW = 0.0;  // weighted "different" mass

	        int[][] nbh = getNeighbourPositionsMoore(currAgent.getPosition(), SOCIAL_CLASS_RADIUS);
	        for (int i = 0; i < nbh.length; i++) {
	            Tile nt = world.getTile(nbh[i][0], nbh[i][1]);
	            if (!nt.hasAgent()) continue;

	            int nc = nt.getagentSocialClass();   // 0..2
	            int dist = Math.abs(nc - myClass);   // 0,1,2

	            // Similarity kernel over class distance:
	            // dist=0 -> 1.0 ; dist=1 -> e^-alpha ; dist=2 -> e^(-2alpha)
	            double diff = ALPHA_CLASS * dist;
	            double sim = 1.0 - diff;

	            clsSameW += sim;
	            clsDiffW += diff;
	        }
  
	        // --- blend race + class into effective similarity (weighted) ---
	        double sameEff = 0; 
		    double diffEff = 0; 
		    
		    
		    
		    switch (myClass)
		    {
		    	case 0:
		    		sameEff = W_COLOR_POOR * sameColor + W_CLASS_POOR * clsSameW;
		    		diffEff = W_COLOR_POOR * diffColor + W_CLASS_POOR * clsDiffW;
		    		break;
		    	case 1:
		    		sameEff = W_COLOR_MEDIUM * sameColor + W_CLASS_MEDIUM * clsSameW;
		    		diffEff = W_COLOR_MEDIUM * diffColor + W_CLASS_MEDIUM * clsDiffW;
		    		break;
		    	case 2:
		    		sameEff = W_COLOR_RICH * sameColor + W_CLASS_RICH * clsSameW;
		    		diffEff = W_COLOR_RICH * diffColor + W_CLASS_RICH * clsDiffW;
		    		break;
		    }
		    
		    
		    
	        double totEff  = sameEff + diffEff;

	        // Guard: no counted neighbors
	        double similarityPct = 0.0;
	        if (totEff > 0.0) {
	            similarityPct = (sameEff / totEff) * 100.0; // keep percent scale
	            
	         
	           
			    
	        }

	        // --- affordability gate (current tile) ---
	        double tileCost = world.getTile(currAgent.posX, currAgent.posY).getRent();
	        double income   = currAgent.getRichness();

	        // Smooth affordability 0..1 (steeper if you want sharper cutoffs)
	        double affordability = affordabilityScore(tileCost, income);
	        // Must both afford AND meet social threshold
	        if (affordability < AFFORDABILITY ) {
	            amHappy = 0; // too expensive → unhappy
	        } else if (similarityPct >= threshold) {
	            amHappy = 1; // socially satisfied and affordable
	        } else {
	        	
	            amHappy = 0; // socially unsatisfied
	        }
	    }

	    if (amHappy != 2) {
	        currAgent.isHappy = amHappy;
	        world.getTile(currAgent.posX, currAgent.posY).setAgentIsHappy(amHappy);
	    } else {
	        print("<<updateHappiness>>amHappy is neither 0 or 1. Is " + amHappy);
	    }
	}


	/**
	 * Method for agents to update their preference for threshold of friends
	 * @param mooreNBHofAgent
	 */
	public void updatePreferences(int agentid, int[] mooreNBHofAgent)
	{
	    
	    Agent currAgent = allAgents.get(agentid);
	    int happy = currAgent.isHappy;
	    boolean isAgentBlue = currAgent.isBlue;

	    double prefMin = 5.00;   // meaning always one same must be present
	    double prefMax = 93.00;  // meaning always one different is tolerated
	    double oldPref = Double.valueOf(df2.format(currAgent.getThreshold())); // e.g., 75.00
	    double newPref = -44;

	    double prefDecrement = -m;
	    double prefIncrement =  m;

	    int same, diff;
	    if (isAgentBlue) {
	        same = mooreNBHofAgent[0]; // moornbh: blue, green, empty
	        diff = mooreNBHofAgent[1];
	    } else {
	        same = mooreNBHofAgent[1];
	        diff = mooreNBHofAgent[0];
	    }

	    // === UPDATED: graded class similarity (distance-weighted) ===================
	    int myClass = currAgent.getSocialClass(); // 0,1,2 (0=poor,1=middle,2=rich)

	    
	    double clsSameW = 0.0;  // weighted "same" mass
	    double clsDiffW = 0.0;  // weighted "different" mass

	    int[][] nbh = getNeighbourPositionsMoore(currAgent.getPosition(), SOCIAL_CLASS_RADIUS);
	    for (int i = 0; i < nbh.length; i++) {
	        Tile nt = world.getTile(nbh[i][0], nbh[i][1]);
	        if (!nt.hasAgent()) continue;

	        int nc = nt.getagentSocialClass();     // 0..2
	        int dist = Math.abs(nc - myClass);     // 0,1,2

	        // Similarity kernel over class distance:
	        // dist=0 -> 1.0 ; dist=1 -> e^-alpha ; dist=2 -> e^(-2alpha)
	        double diffW = ALPHA_CLASS * dist;
            double sim = 1.0 - diffW;

	        clsSameW += sim;
	        clsDiffW += diffW;
	    }

	    // Blend color + class (graded-by-distance)

	    double sameEff = 0; 
	    double diffEff = 0; 
	    
	    switch (myClass)
	    {
	    	case 0:
	    		sameEff = W_COLOR_POOR * same + W_CLASS_POOR * clsSameW;
	    		diffEff = W_COLOR_POOR * diff + W_CLASS_POOR * clsDiffW;
	    		break;
	    	case 1:
	    		sameEff = W_COLOR_MEDIUM * same + W_CLASS_MEDIUM * clsSameW;
	    		diffEff = W_COLOR_MEDIUM * diff + W_CLASS_MEDIUM * clsDiffW;
	    		break;
	    	case 2:
	    		sameEff = W_COLOR_RICH * same + W_CLASS_RICH * clsSameW;
	    		diffEff = W_COLOR_RICH * diff + W_CLASS_RICH * clsDiffW;
	    		break;
	    }
	    
	    
        double totEff  = sameEff + diffEff;

	    if (totEff == 0.0) // isolated (or nobody counted) → keep preference unchanged
	    {
	        newPref = oldPref;
	    }
	    else
	    {
	        if (diffEff > 0.5) // some “difference” signal exists (from color and/or class distance)
	        {

	            if (happy == 1) {
	            	
	                newPref = oldPref + prefDecrement; // happy among some different → relax a bit
	            } else if (happy == 0) {
	                newPref = oldPref;                 // unhappy → hold steady
	            } else {
	                print("<<updatePreferences>>happiness neither 0 nor 1. is " + happy);
	            }
	        }
	        else /* diffEff == 0.0 */ // perfectly homogeneous neighbourhood (by both color and class kernel)
	        {
	            if (happy == 1) {
	                newPref = oldPref + prefIncrement; // homogeneous + happy → gets stricter
	            } else if (happy == 0) {
	                newPref = oldPref;                 // unhappy for other reasons (e.g., rent) → unchanged
	            } else {
	                print("<<updatePreferences>>happiness neither 0 nor 1. is " + happy);
	            }
	        }
	    }

	    // Clamp to allowed bounds
	    if (newPref < prefMin) newPref = prefMin;
	    else if (newPref > prefMax) newPref = prefMax;

	    if (newPref < prefMin || newPref > prefMax) {
	        print("<updatePreferences> newPref invalid. It's " + newPref + ". Min/Max: " + prefMin + "/" + prefMax);
	        print("<<updatePreferences>>Is " + newPref + " > " + prefMax + "? " + (newPref > prefMax));
	        print("<<updatePreferences>>Is " + newPref + " < " + prefMin + "? " + (newPref < prefMin));
	        print("<<updatePreferences>>isAgent blue? " + currAgent.isBlue + ". Is agent happy? " + currAgent.isHappy + ". oldPref " + oldPref);
	        print("<<updatePreferences>>differents: " + diff + ". totEff: " + totEff + ". ");
	        System.exit(0);
	    }

	    if (newPref != oldPref) {
	        currAgent.setThreshold(newPref);
	        int[] apos = currAgent.getPosition();
	        world.getTile(apos[0], apos[1]).setAgentAvgF(newPref);
	        world.getTile(apos[0], apos[1]).setagentRichness(currAgent.getRichness());
	        world.getTile(apos[0], apos[1]).setagentSocialClass(currAgent.getSocialClass());
	    }	
	}
	
	
	
	//  INCOMES
	
	public static int generateAgentIncome(double median, double gini, long seed) {
	    // Compute μ and σ from median and Gini
	    double mu = Math.log(median);
	    double sigma = Math.sqrt(2) * inverseNormal((gini + 1) / 2.0);

	    // Random lognormal draw
	    Random rand = new Random(seed);
	    double z = rand.nextGaussian();
	    double income = Math.exp(mu + sigma * z);

	    // Round and guard against 0 or negative
	    int rounded = (int) Math.max(1, Math.round(income));
	    
	    return rounded;
	}

	public static int classifyIncome(double income, double median) {
	    if (income < 0.75 * median) return 0;
	    if (income < 2.0 * median) return 1;
	    return 2;
	}
	
	private double affordabilityScore(double rent, double income){
	    return 1.0 / (1.0 + Math.exp((rent - income) / 200.0));
	}


	private double localMeanIncome(int cx, int cy, int radius) {
	    double sum = 0.0, wsum = 0.0;

	    for (int dx = -radius; dx <= radius; dx++) {
	        for (int dy = -radius; dy <= radius; dy++) {
	            if (!INCLUDE_CENTER && dx == 0 && dy == 0) continue;

	            int nx = wrap(cx + dx, sizeX);
	            int ny = wrap(cy + dy, sizeY);
	            Tile t = world.getTile(nx, ny);

	            if (t.hasAgent()) {
	                double w = WEIGHT_BY_DISTANCE ? ringWeight(dx, dy) : 1.0;
	                sum  += t.getagentRichness() * w;
	                wsum += w;
	            }
	        }
	    }
	    // Fallback to your baseline if no neighbors with agents
	    return (wsum > 0.0) ? (sum / wsum) : nativeIncomeMedian;
	}

	private double localOccupancy(int cx, int cy, int radius) {
	    double occSum = 0.0, wsum = 0.0;

	    for (int dx = -radius; dx <= radius; dx++) {
	        for (int dy = -radius; dy <= radius; dy++) {
	            if (!INCLUDE_CENTER && dx == 0 && dy == 0) continue;

	            int nx = wrap(cx + dx, sizeX);
	            int ny = wrap(cy + dy, sizeY);
	            double w = WEIGHT_BY_DISTANCE ? ringWeight(dx, dy) : 1.0;

	            if (world.getTile(nx, ny).hasAgent()) occSum += w;
	            wsum += w;
	        }
	    }
	    // occupancy in [0,1]
	    return (wsum > 0.0) ? (occSum / wsum) : 0.0;
	}
	

	private void updateRent() {
	    // Make sure ALPHA in [0,1]
	    double a = clamp01(ALPHA);

	    for (int x = 0; x < world.getSizeX(); x++) {
	        for (int y = 0; y < world.getSizeY(); y++) {
	            Tile t = world.getTile(x, y);

	            // --- Neighborhood stats ---
	            double i   = localMeanIncome(x, y, RENT_RADIUS); // average income (weighted if enabled)
	            double occ = localOccupancy(x, y, RENT_RADIUS);  // 0..1
	            double vac = 1.0 - occ;

	            // --- Raw cost model (yours) ---
	            double costHat = BASELINE_COST + INCOME_SENSIVITY * i - LOW_DENSITY_SENSIVITY * vac + HIGH_DENSITY_SENSIVITY * occ;

	            // --- Temporal smoothing ---
	            double prev = t.getRent();
	            double cost = (1.0 - a) * prev + a * costHat;

	            // --- Guardrails ---
	            if (Double.isNaN(cost) || Double.isInfinite(cost)) cost = RENT_FLOOR;
	            if (cost < RENT_FLOOR) cost = RENT_FLOOR;
	            if (cost > RENT_CEIL)  cost = RENT_CEIL;

	            t.setRent(cost);
	        }
	    }
	}



	
	


	


	/**
	 * the agent movement method.<br>
	 * first, the new position gets determined by calling
	 * moveToABetterPlace().<br>
	 * second, the agent gets removed from current tile.<br>
	 * third, the agent gets added to the newly determined tile.<br>
	 */
	public void agentMove(int agentIndex)
	{
		int[] newPos = { -1, -1 };
		newPos = moveToABetterPlace(agentIndex);
		boolean placedSuccessfully = false;

		if (newPos[0] == -1 || newPos[1] == -1)
		{
			print("A new position could not be found for our agentIndex " + agentIndex + ". Not moving.");
			throw new IllegalArgumentException();
		}

		else if (newPos[0] == -44 && newPos[1] == -44)
		{
			changedMind++;
			// print("<<agentMove>>newPos is = -44,-44. That means no better tile was found. not moving now.");
		}
		else
		{
			world.removeAgentFromTile(allAgents.get(agentIndex).posX, allAgents.get(agentIndex).posY); // remove agent from tile
			placedSuccessfully = world.placeAgentOnTile(newPos[0], newPos[1], allAgents.get(agentIndex).isBlue, allAgents.get(agentIndex).getHappy(), allAgents.get(agentIndex).getThreshold(), 
					 allAgents.get(agentIndex).getRichness(),  allAgents.get(agentIndex).getSocialClass());
			if (placedSuccessfully == false)
			{
				print("<<agentMove>>does emptyTiles contain the tile with the new position? " + emptyTiles.contains(world.getTile(newPos[0], newPos[1])));
				print("<<agentMove>>Trying to place agent number " + agentIndex + " at " + allAgents.get(agentIndex).posX + "," + allAgents.get(agentIndex).posY + " on tile " + newPos[0] + ","
						+ newPos[1] + " but that tile says: 'hasAgent?' - " + world.getTile(newPos[0], newPos[1]).hasAgent() + "!");
				throw new IllegalArgumentException();
			}
			emptyTiles.remove(world.getTile(newPos[0], newPos[1]));
			emptyTiles.add(world.getTile(allAgents.get(agentIndex).posX, allAgents.get(agentIndex).posY));
			allAgents.get(agentIndex).setPosition(newPos[0], newPos[1]);
			allAgents.get(agentIndex).moveCounter++;

			globalMoveCounter++;
		}
	}

	public int[] moveRandomly(int agentIndex)
	{
		int[] newPos = { -3, -3 };
		boolean placedSuccessfully = false;
		int rngMax = emptyTiles.size();
		Tile currRngTile = emptyTiles.get(prng.nextInt(rngMax));

		newPos[0] = currRngTile.getPosition()[0];
		newPos[1] = currRngTile.getPosition()[1];

		if (newPos[0] == -3 || newPos[1] == -3)
		{
			print("<<moveRandomly>>A new <random> position could not be found for our agentIndex " + agentIndex + ". Not moving.");
			throw new IllegalArgumentException();
		}
		else
		{
			world.removeAgentFromTile(allAgents.get(agentIndex).posX, allAgents.get(agentIndex).posY);
			placedSuccessfully = world.placeAgentOnTile(newPos[0], newPos[1], allAgents.get(agentIndex).isBlue, allAgents.get(agentIndex).isHappy, allAgents.get(agentIndex).getThreshold(), 
					allAgents.get(agentIndex).getRichness(), allAgents.get(agentIndex).getSocialClass());
			if (placedSuccessfully == false)
			{
				print("<<moveRandomly>>does emptyTiles contain the tile with the new position? " + emptyTiles.contains(world.getTile(newPos[0], newPos[1])));
				print("<<moveRandomly>>Trying to place agent number " + agentIndex + " at " + allAgents.get(agentIndex).posX + "," + allAgents.get(agentIndex).posY + " on tile " + newPos[0] + ","
						+ newPos[1] + " but that tile says: 'hasAgent?' - " + world.getTile(newPos[0], newPos[1]).hasAgent() + "!");

				throw new IllegalArgumentException();

			}
		}
		emptyTiles.remove(world.getTile(newPos[0], newPos[1]));
		emptyTiles.add(world.getTile(allAgents.get(agentIndex).posX, allAgents.get(agentIndex).posY));
		allAgents.get(agentIndex).setPosition(newPos[0], newPos[1]);
		allAgents.get(agentIndex).moveCounter++;

		globalMoveCounter++;

		return newPos;
	}

	/**
	 * This main method for finding the best place to move to after agents
	 * determine they are unhappy works as follows:<br>
	 * of every empty tile on the map, we get the neighbourhood
	 * information via checkMooreNBH();<br>
	 * it returns the counts for blue, greens and empties. <br>
	 * then we take the ratio of each and store that information. the empty tile
	 * with the best ratio wins the betterPlace contest.<br>
	 * the logic varies depending on whether the agent itself is blue or
	 * green.<br>
	 * ### IF UTILITY ON: (Default: true)
	 * Abolished the ratio logic. Agents should still refer to their threshold,
	 * and any tile equal to or better than the demands should be deemed of
	 * equal value. The ratio logic of simply subtracting green - blue (or vice
	 * versa) is much more strict as it will always favour the absolute best of
	 * all possible considered (w) tiles. (traditional Schelling)
	 * 
	 * @return int[] position x,y
	 **/
	public int[] moveToABetterPlace(int agentNum)
	{
		// print("<<moveToABetterPlace>>moving to a better place now. emptyTiles.size() is " + emptyTiles.size() + ".");
		Agent curA = allAgents.get(agentNum);
		int betterPlace[] = { -1, -1 }; // future position agent moves to
		int[] consideredTiles = generateUniqueNumbers(w);
		int[] surroundingInfo = new int[3]; // 0=blue,// 1=green,// 2=empty
		double total;
		List<int[]> emptyTileInfo = new ArrayList<int[]>(); // x, y, same, different, utility
		int eligibleChoicesThisAgent = 0;
		
		// NEW: identify race/class for split metrics
		int raceIdx  = curA.isBlue ? 1 : 0;          // 0=Green, 1=Blue
		int classIdx = curA.getSocialClass();        // 0..2

		// NEW: count this unhappy agent in the split counters
		unhappyCheckedByRace[raceIdx]++;
		unhappyCheckedByClass[classIdx]++;
		// here we loop through the list of empty tiles. for each empty tile, we
		// get its mooreNBH information and store that in our emptyTileInfo list.
		if (consideredTiles.length <= emptyTiles.size())
		{
			for (int i = 0; i < consideredTiles.length; i++)
			{
				int[] allInfo = new int[5]; // 0=x, 1=y, 2=same, 3=different, 4=utility
				Tile cand = emptyTiles.get(consideredTiles[i]);
				allInfo[0] = emptyTiles.get(consideredTiles[i]).position[0]; // x coordinate
				allInfo[1] = emptyTiles.get(consideredTiles[i]).position[1]; // y coordinate
				
				
				
				// === 1) Affordability check (sigmoid gate) ==========================
			    double tileCost = cand.rent;                 // or cand.getRent()
			    double income   = curA.getRichness();
			    double affordability = affordabilityScore(tileCost, income);

			    // skip tile if too unaffordable (score below threshold)
			    if (affordability < AFFORDABILITY ) {                   
			        allInfo[2] = 0; allInfo[3] = 0; allInfo[4] = 0;
			        emptyTileInfo.add(allInfo);
			        continue;
			    }
			    // =====================================================================

			    // === 2) Colour (race) similarity counts ==============================
			    surroundingInfo = checkNBH(cand.position); // [blue, green, empty]
			    int sameColor, diffColor;
			    if (curA.isBlue) {
			        sameColor = surroundingInfo[0];
			        diffColor = surroundingInfo[1];
			    } else {
			        sameColor = surroundingInfo[1];
			        diffColor = surroundingInfo[0];
			    }
			    // =====================================================================

			    // === 3) Class similarity counts ======================================
			    int myClass = curA.getSocialClass(); // 0,1,2
			    double clsSameW = 0.0;  // weighted "same" mass
			    double clsDiffW = 0.0;  // weighted "different" mass

			    int[][] nbh = getNeighbourPositionsMoore(curA.getPosition(), SOCIAL_CLASS_RADIUS);
			    for (int i1 = 0; i1 < nbh.length; i1++) {
			        Tile nt = world.getTile(nbh[i1][0], nbh[i1][1]);
			        if (!nt.hasAgent()) continue;

			        int nc = nt.getagentSocialClass();     // 0..2
			        int dist = Math.abs(nc - myClass);     // 0,1,2

			        // Similarity kernel over class distance:
			        // dist=0 -> 1.0 ; dist=1 -> e^-alpha ; dist=2 -> e^(-2alpha)
			        double diff = ALPHA_CLASS * dist;
		            double sim = 1.0 - diff;

			        clsSameW += sim;
			        clsDiffW += diff;
			    }

			    // === 4) Blend race + class into similarity ===========================
			    double sameEff = 0; 
			    double diffEff = 0; 
			    
			    
			    switch (myClass)
			    {
			    	case 0:
			    		sameEff = W_COLOR_POOR * sameColor + W_CLASS_POOR * clsSameW;
			    		diffEff = W_COLOR_POOR * diffColor + W_CLASS_POOR * clsDiffW;
			    		break;
			    	case 1:
			    		sameEff = W_COLOR_MEDIUM * sameColor + W_CLASS_MEDIUM * clsSameW;
			    		diffEff = W_COLOR_MEDIUM * diffColor + W_CLASS_MEDIUM * clsDiffW;
			    		break;
			    	case 2:
			    		sameEff = W_COLOR_RICH * sameColor + W_CLASS_RICH * clsSameW;
			    		diffEff = W_COLOR_RICH * diffColor + W_CLASS_RICH * clsDiffW;
			    		break;
			    }
			    
			    double totEff  = sameEff + diffEff;

			    allInfo[2] = (int)Math.round(sameEff);
			    allInfo[3] = (int)Math.round(diffEff);
			    if (totEff <= 0.0) { allInfo[4] = 0; emptyTileInfo.add(allInfo); continue; }
			    // =====================================================================

			    // === 5) Utility: purely social, affordability used only as gate ======
			    double similarity = sameEff / totEff;
			    double threshold  = curA.getThreshold() / 100.0;
			    allInfo[4] = (similarity >= threshold) ? 1 : 0;
			    
			    if (allInfo[4] == 1) {
			        eligibleChoicesThisAgent++;
			    }
			    //if(myClass == 2 &&  allInfo[4] == 1) {System.out.println(allInfo[4] + "   " + threshold + "      " + similarity);}
			    // =====================================================================

			    emptyTileInfo.add(allInfo);
			}
		}
		else
		{
			print("the number of considered tiles is higher than the number of empty tiles.");
			throw new IllegalArgumentException();
		}

		// now we have all neighbours and their appeal rating. now we need to get the highest one
		// first things first, put all appeals in an array to see whether there are duplicates etc
		int[] ratioArray = new int[emptyTileInfo.size()]; // array is as long as the surrounding list looping through the surrounding list
		for (int j = 0; j < emptyTileInfo.size(); j++)
		{
			ratioArray[j] = emptyTileInfo.get(j)[4];// adding all the appeals
		}

		// now we loop through our appeal array to find the highest value: [1, 1, 1, 0, 0, 1] then we want to get the 1s
		int bestUtility = 1; // utility = 1
		int duplicates = 0;
		for (int t = 0; t < ratioArray.length; t++)
		{
			// print("<<moveToABetterPlace>>is ratioArray[t] " + ratioArray[t] + " == " + " bestUtility " + bestUtility + "?");
			if (ratioArray[t] == bestUtility)
			{
				duplicates++;
			}
		}

		List<int[]> narrowedCandidates = new ArrayList<int[]>(); // list of positions of suitable tiles
		List<int[]> narrowedCrappyCandidates = new ArrayList<int[]>();

		// if the number of duplicates is not equal the number of tiles (i.e. not all tiles are the same)...
		if (duplicates < ratioArray.length)
		{
			for (int t = 0; t < emptyTileInfo.size(); t++) // loop through our tile list
			{
				if (emptyTileInfo.get(t)[4] == bestUtility) // and add only the ones that have the best ratio
				{
					int[] position = { emptyTileInfo.get(t)[0], emptyTileInfo.get(t)[1] };
					narrowedCandidates.add(position);
				}
				else
				{
					int[] position = { emptyTileInfo.get(t)[0], emptyTileInfo.get(t)[1] };
					narrowedCrappyCandidates.add(position);
				}
			}
		}
		else if (duplicates == ratioArray.length)
		// all tiles in the list are of the same value to the agent! we just add them all to the narrowed list
		{
			for (int l = 0; l < emptyTileInfo.size(); l++)
			{
				int[] position = { emptyTileInfo.get(l)[0], emptyTileInfo.get(l)[1] };
				narrowedCandidates.add(position);
			}
		}
		// NEW: split aggregates
		totalChoicesByRace[raceIdx]  += eligibleChoicesThisAgent;
		totalChoicesByClass[classIdx] += eligibleChoicesThisAgent;

		// now we have a list of narrowed-down candidates. if the list is larger
		// than 1, we have multiple tiles of equal value.
		// in that case, we randomize which tile gets picked by shuffling and
		// always picking the first element
		if (narrowedCandidates.size() > 1) // if we have more than one tile with the same highest influx, randomize
		{
			Collections.shuffle(narrowedCandidates, new Random(System.nanoTime())); // tested & working
			betterPlace = narrowedCandidates.get(0);
		}
		else if (narrowedCandidates.size() == 1) // if we have only one winner... bingo!
		{
			betterPlace = narrowedCandidates.get(0);
		}
		else // narrowedCandidates.size() is smaller than 1, i.e. none of the choices had utility = 1. we just pick any tile.
		{
			if (utilityOn == false)
			{
				Collections.shuffle(narrowedCrappyCandidates, new Random(System.nanoTime()));
				betterPlace = narrowedCrappyCandidates.get(0);
			}
			else if (utilityOn) // if on, agents will actually not move to a better place if there is none
			{
				betterPlace[0] = -44;
				betterPlace[1] = -44;
				failedMoveAttemptsByRace[raceIdx]++;
				failedMoveAttemptsByClass[classIdx]++;
			}
		}
		return betterPlace;
	}
	
	


	/**
	 * This is the main method for the agents.<br>
	 **/
	public void invokeAgentActions()
	{
		fireEvent(new TickStart());

		for (int i = 0; i < allAgents.size(); i++)
		{
			Agent currAgent = allAgents.get(i);
			int[] mooreNBHofAgent = checkNBH(currAgent.pos);
			updateHappiness(i, mooreNBHofAgent);
			updatePreferences(i, mooreNBHofAgent); // update preference before movement decision

			if (currAgent.isHappy == 0)// unhappy
			{
				agentMove(i);
			}

			else if (currAgent.isHappy == 1)// happy
			{
				if (prng.nextInt(1000) == 72) // small chance of random movement.
				{
					moveRandomly(i);
				}

			}
			else
			{
				print("happyval invalid. Should be 0 or 1, is " + currAgent.isHappy);
				System.exit(0);
			}
			collectEmptyTiles();

		}
		fireEvent(new AgentMovedEvent());
	}
	



	public void initAppeal()
	{
		for (int x = 0; x < world.getSizeX(); x++)
		{
			for (int y = 0; y < world.getSizeY(); y++)
			{
				world.getTile(x, y).setAppeal(0); // by default, first set all tiles to 0 appeal
			}
		}
		csvAppealValues = new int[world.getSizeX()][world.getSizeY()];
		try
		{
			initGridAppeal(1);
		}
		catch (IOException e)
		{
			print("Something went wrong in initAppeal");
			e.printStackTrace();
		}
	}
	
	public void initGridAppeal(int optionSelect) throws IOException
	{
		if (optionSelect == 1) //randomize the appeal of the starting grid (default)
		{
			for (int x = 0; x < world.getSizeX(); x++)
			{
				for (int y = 0; y < world.getSizeY(); y++)
				{
					world.getTile(x, y).setAppeal(prng.nextInt(5));
				}
			}
		}

		else if (optionSelect == 3) //set the appeal to 0 for all tiles of the starting grid
		{
			for (int x = 0; x < world.getSizeX(); x++)
			{
				for (int y = 0; y < world.getSizeY(); y++)
				{
					world.getTile(x, y).setAppeal(0);;
				}
			}
		}
		else
		{
			System.out.println("no Grid initialization option selected.");
		}
		countAppealData();
	}

	public void updateAppeal()
	{
		for (int x = 0; x < world.getSizeX(); x++)
		{
			for (int y = 0; y < world.getSizeY(); y++)
			{
				Tile currT = world.getTile(x, y);
				if (!currT.hasAgent())
				{
					int counter = 0;
					int[][] nbh = getNeighbourPositionsVNeu1(currT.getPosition());
					for (int i = 0; i < nbh.length; i++)
					{
						if (world.getTile(nbh[i][0], nbh[i][1]).hasAgent())
						{
							counter++;
						}
					}
					currT.setAppeal(counter);
				}
				else //if tiles has agent, appeal = 5 
				{
					currT.setAppeal(5);
				}
			}
		}
	}

	
	public int randomizeAppeal()
	{
		Random rng = new Random();
		int appeal = rng.nextInt(5);
		return appeal;
	}
	
	
	public void countAppealData()
	{
		int[] appealRatings = { 0, 0, 0, 0, 0, 0 };// 0 rating, 1 rating, 2
													// rating, 3 rating, 4
													// rating, 5 rating
		for (int x = 0; x < world.getSizeX(); x++)
		{
			for (int y = 0; y < world.getSizeY(); y++)
			{
				int currAppeal = world.getTile(x, y).getAppeal();
				if (currAppeal == 0)
				{
					appealRatings[0]++;
				}
				else if (currAppeal == 1)
				{
					appealRatings[1]++;
				}
				else if (currAppeal == 2)
				{
					appealRatings[2]++;
				}
				else if (currAppeal == 3)
				{
					appealRatings[3]++;
				}
				else if (currAppeal == 4)
				{
					appealRatings[4]++;
				}
				else if (currAppeal == 5)
				{
					appealRatings[5]++;
				}

				else
				{
					print("<<countAppealData>> Appeal rating not between 0 and 5, or -1:" + currAppeal);
				}

			}

		}
	}
	
	

	public int getInfluxNbours(Tile tile)
	{
		int counter = 0;
		int[][] neighbours = null;

		neighbours = getNeighbourPositionsMoore(tile.getPosition(), 2);

		for (int i = 0; i < neighbours.length; i++)
		{
			if (world.getTile(neighbours[i][0], neighbours[i][1]).belongsToInflux)
			{
				counter++;
			}
		}
		return counter;
	}
	
	


	private int[][] getNeighbourPositionsVNeu1(int[] currPos)
	{
		int[][] neighbourPositionsFour = new int[4][2];

		neighbourPositionsFour[0][0] = currPos[0]; // up
		neighbourPositionsFour[0][1] = currPos[1] - 1; // up

		neighbourPositionsFour[1][0] = currPos[0] + 1; // right
		neighbourPositionsFour[1][1] = currPos[1]; // right

		neighbourPositionsFour[2][0] = currPos[0]; // down
		neighbourPositionsFour[2][1] = currPos[1] + 1; // down

		neighbourPositionsFour[3][0] = currPos[0] - 1; // left
		neighbourPositionsFour[3][1] = currPos[1]; // left

		for (int z = 0; z < 4; z++)
		{
			if (neighbourPositionsFour[z][0] >= sizeX)
			{
				neighbourPositionsFour[z][0] = neighbourPositionsFour[z][0] - sizeX;
			}
			else if (neighbourPositionsFour[z][0] < 0)
			{
				neighbourPositionsFour[z][0] = neighbourPositionsFour[z][0] + sizeX;
			}
			if (neighbourPositionsFour[z][1] >= sizeY)
			{
				neighbourPositionsFour[z][1] = neighbourPositionsFour[z][1] - sizeY;
			}
			else if (neighbourPositionsFour[z][1] < 0)
			{
				neighbourPositionsFour[z][1] = neighbourPositionsFour[z][1] + sizeY;
			}
		}
		return neighbourPositionsFour;
	}



	/**
	 * Method for retrieving the most appealing neighbour.
	 * @param int[]  (position of current tile)
	 * @return int[] (position of the determined best neighbour)
	 * Long method, read comments
	 **/
	public int[] checkMostAppealingNeighbour(int[] pos)
	{
		// we create a List of integer arrays. The list concerns itself with the
		// surrounding tiles of a given tile. Each entry has 4 integers
		List<int[]> surroundingTiles = new ArrayList<int[]>();// 0=x, 1=y, 2=appeal, 3=influxNbours
		int[][] tempAgntsNeigPos = getNeighbourPositionsMoore(pos, 2); // we get the neighbour positions (x,y int) of the current tile
		int[] mostAppealingNeighbour = { -1, -1 }; // the position of the most appealing neighbour that we want to determine

		// looping through the neighbour positions
		for (int g = 0; g < tempAgntsNeigPos.length; g++)
		{
			// retrieving the associated tile of that position. [x][0] and
			// [x][1] are always the x and y positions
			Tile currTile = world.getTile(tempAgntsNeigPos[g][0], tempAgntsNeigPos[g][1]);
			// now we can work with the tile. checking whether the tile is free and doesn't belong to influx:
			if (!currTile.hasAgent() && !currTile.belongsToInflux)
			{
				// if so, we want to store the position, appeal rating and how
				// many influxes surround the tile- stored in a neat array
				int[] allStuff = new int[4];
				allStuff[0] = currTile.getPosition()[0];
				allStuff[1] = currTile.getPosition()[1];
				allStuff[2] = currTile.getAppeal();
				allStuff[3] = getInfluxNbours(currTile);
				// that array gets added to our surrounding-list.
				surroundingTiles.add(allStuff);
			}
		}

		// now we have a list of surrounding tiles that don't have agents and don't have an influx flag.
		if (surroundingTiles.size() == 1) // if the list contains only one entry, it's easy- the most appealing neighbour is the only list entry.
		{
			mostAppealingNeighbour = surroundingTiles.get(0);
		}

		// if it contains more than one entry, we need to sort by the highest appeal, which is the next criteria
		else if (surroundingTiles.size() > 1)
		{
			// now we have all neighbours and their appeal rating. now we need to get the highest one
			// (but not 5, as that indicates an agent on the tile.) first things
			// first, put all appeals in an array to see whether there are duplicates etc
			int[] appealArray = new int[surroundingTiles.size()]; // array is as long as the surrounding list

			// looping through the surrounding list...
			for (int j = 0; j < surroundingTiles.size(); j++)
			{
				// adding all the appeals
				appealArray[j] = surroundingTiles.get(j)[2];
			}

			// now we loop through our appeal array to find the highest value
			// lets assume: [0, 4, 2, 5, 4, 3, 4, 3] then we would want to get the 4
			int bestAppeal = -1;
			int duplicates = 0;
			for (int w = 0; w < appealArray.length; w++)
			{
				if (appealArray[w] > bestAppeal && appealArray[w] != 5) // appeal == 5 implicates agent on that tile
				{
					bestAppeal = appealArray[w];
				}

				if (appealArray[w] == bestAppeal)
				{
					duplicates++;
				}
			}

			//create a new list for all winning tiles with bestAppeal
			List<int[]> notsure = new ArrayList<int[]>();
			// if the number of duplicates is not equal to the array length, that means we have a winner.
			// we don't need to resort to influx-counts, we use the appeal
			if (!(duplicates == appealArray.length))
			{
				for (int t = 0; t < surroundingTiles.size(); t++)
				{
					if (surroundingTiles.get(t)[2] == bestAppeal)
					{
						int[] position = { surroundingTiles.get(t)[0], surroundingTiles.get(t)[1] };
						notsure.add(position);
					}
				}
			}

			// if all elements in the appeal array are the same, go prioritise
			// those tiles with the most influx neighbours
			else if ((duplicates == appealArray.length))
			{
				int[] influxArray = new int[surroundingTiles.size()];
				for (int j = 0; j < surroundingTiles.size(); j++)
				{
					influxArray[j] = surroundingTiles.get(j)[3]; // store all influx counters in an array
				}

				// then we try and find the best influx...
				int bestInflux = -1;
				for (int w = 0; w < influxArray.length; w++)
				{
					if (influxArray[w] > bestInflux)
					{
						bestInflux = influxArray[w];
					}
				}

				// and THEN we get the best influx tiles and add them to our notsure list.
				for (int t = 0; t < surroundingTiles.size(); t++)
				{
					if (surroundingTiles.get(t)[3] == bestInflux)
					{
						int[] position = { surroundingTiles.get(t)[0], surroundingTiles.get(t)[1] };
						notsure.add(position);
					}
				}
			}

			if (notsure.size() > 1) // if we have more than one tile with the same highest influx, randomize
			{
				Collections.shuffle(notsure); 
				mostAppealingNeighbour = notsure.get(0);
			}

			else if (notsure.size() == 1) // if we have only one winner... bingo!
			{
				mostAppealingNeighbour = notsure.get(0);
			}

			else // this should not happen. because we start out from an influx position, there should always be at least 1.
			{
				print("<<checkMostAppealingNeighbour>>There were no influxes or appeals recorded for any neighbours, notsureList is empty");
				print(notsure.get(0));
				System.exit(-2);
			}

		}

		// if there are no free tiles, we pick a random neighbour and try our luck. that way, the next influx is always "in the neighbourhood".
		else
		{
			int rngx = prng.nextInt(8);
			print("<<checkMostAppealingNeighbour>>no free tiles. surroundingTiles is empty. trying " + tempAgntsNeigPos[rngx][0] + "," + tempAgntsNeigPos[rngx][1] + " now.");
			mostAppealingNeighbour = checkMostAppealingNeighbour(tempAgntsNeigPos[rngx]);
		}

		return mostAppealingNeighbour;
	}



	/** Method to calculate HOW BIG an influx should be, given a percentage */
	public int calculateInfluxHOWBIG(int influxpercent, double numofagents)
	{
		double pc = influxpercent * 1.0;
		Double tempPc = (pc / 100);
		Double percentOfCurrentPop = numofagents * tempPc;
		int percentCurrentPop = percentOfCurrentPop.intValue();
		return percentCurrentPop;
	}

	/**
	 * Method to calculate WHEN influxes should occur, given a set amount of
	 * fluxes
	 */
	public ArrayList<int[]> calculateInfluxWHEN(int noofinfluxes)
	{
		Double maxticks = (double) 1000;
		//Double fivPCofmax = maxticks * 0.05; // say, of 1000 maxticks, 5% = 50 ticks
		Double tenPCofmax = maxticks * 0.1; // say, of 1000 maxticks, 10% = 100 ticks

		int starttick = tenPCofmax.intValue(); // starttick would be 100
		//int starttick = fivPCofmax.intValue(); // starttick would be 50
		int endtick = 1000 - starttick; // endTick would be 1000-100 = 900
		int totalconsideredticks = endtick - starttick; // 900-100 = 800
		int ticknumber = totalconsideredticks / noofinfluxes; // so if influxes = 4, we'd have 800/4=200. so every 200 ticks, influx
		if (ticknumber < 1)
		{
			print("<<calculateInfluxWHEN>>" + totalconsideredticks + " < " + noofinfluxes + ". Too few ticks for even distribution.");
			System.exit(0);
		}

		current_size = maxfluxsize / noofinfluxes;
		int reach = current_size * noofinfluxes;
		int diff = maxfluxsize - reach;

		// List of int[], length is = noofinfluxes. so for influx 15, length is 15 x 2[actual tick, actual size]
		ArrayList<int[]> ticksAndSizes = new ArrayList<int[]>();
		for (int i = 0; i < noofinfluxes; i++)
		{
			int thetick = starttick + (ticknumber * i);
			int[] meh = { thetick, current_size };
			ticksAndSizes.add(meh);
		}

		// because the number of influxes is preset, the size of each influx has
		// to be calculated using maximum agents and number of influxes.
		// the problem is that due to divisions resulting in fractions (and
		// agents must be whole integers), the accuracy of the estimated
		// migrants to flux goes down the higher the number of fluxes is.
		// Accuracy is perfect for 1x, close enough for 4x, sometimes off for
		// x15 and wildly inaccurate for 100x. The differences are between 0.04
		// and 3.48% density. The threshold that should not be exceeded is
		// 0.24%.
		// that is less than a quarter of density different and can be rounded
		// down. I.e. 87.24% density is rounded down to 87%. But if the
		// actual density differs by more than that, the missing agents should
		// be added.

		if (diff > 6) // the difference of agents that should enter, and that do enter, should not exceed 6, which is 0.24% density
		{
			for (int p = 0; p < diff; p++)
			{
				int rnd = new Random().nextInt(ticksAndSizes.size());
				int[] data = new int[2];
				data = ticksAndSizes.get(rnd); // pick a random entry from the tick list
				data[1] = data[1] + 1; // add one agent to that entry. i.e.: tick 630, 9 agents -> tick 630, 10 agents
				ticksAndSizes.set(rnd, data);
				// print("<<calculateInfluxWHEN>>" + p + 1 + ") at tick " + ticksAndSizes.get(rnd)[0] + "
				// there are now " + data[1] + " agents instead of " + current_size + ".");
			}
		}
		return ticksAndSizes;
	}
	
	
	/**
	 * Method that deals with the influx of agents.<br>
	 * Making sure that the number of agents to be added does not exceed the
	 * total number of free tiles (if crowded) and that the number of agents to
	 * be added does not exceed the maximum number of those to be added (like,
	 * 150 at a time), it finds space starting from the best starting position
	 * that was determined earlier.
	 **/
	public void flux(Double dinfluxsize, int rx, int ry)
	{
		int[] pos = { rx, ry }; // store x and y in an integer array
		influxPositions = new ArrayList<int[]>(); // list of tiles (defined as x-y int array) that agents will be place on
		boolean isBlue = true;// migrants are blue
		double newAgentTolerance;
		int money;
		while (influxPositions.size() < dinfluxsize) // while the list of positions is smaller than the max number of migrants to be added...
		{
			// print("<<flux>> going to findSpace now... influxsize: " + influxsize + ". influxPositions.size(): " + influxPositions.size());
			findSpace(pos); // find space! starting at the given position
		}
		// print("<<flux>> findSpace survived! influxsize: " + dinfluxsize + ". influxPositions.size(): " + influxPositions.size());

		if (influxPositions.size() == numBlues)
		{

		}
		// now that we have all the positions in the arraylist, we loop through the list, placing a new migrant at each position.
		for (int i = 0; i < influxPositions.size(); i++)
		{
			
			money = generateAgentIncome(migrantIncomeMedian, migrantGINI, System.nanoTime());
			int socialClass = classifyIncome(money, nativeIncomeMedian);
			//because we have different type of influxes, they result in a different kind of blue population:
			
			newAgentTolerance = generateThreshold(); //1 uniform

			if (world.getTile(influxPositions.get(i)[0], influxPositions.get(i)[1]).addAgent(isBlue, 2, newAgentTolerance, money, socialClass))
			{   // if the agent is indeed placed in the world..
				// ...then proceed to add that migrant to the migrant list, using the same position ofc
				allAgents.add(new Agent(influxPositions.get(i)[0], influxPositions.get(i)[1], isBlue, newAgentTolerance, 2, money, socialClass, generateThreshold()));
				
			}
		}

		// clearing all the influx tiles (cyan tiles)
		world.removeBelongsToInflux();
		// clearing the influx position list
		influxPositions.clear();
	}

	
	

	/**
	 * Picking the best starting tile for new influxes to start. It loops
	 * through the world, collects the appeal info of all tiles without agents,
	 * and then loops through those tiles, picking the best.
	 * 
	 * Right now it's crude: favour all tiles with 4 or 5 appeal. If that leaves
	 * 0 tiles, go for 3 ratings instead. No catch-clauses for less than 3
	 * rating at the moment.
	 */
	public int[] collectBestStart()
	{
		int[] bestPos = { -1, -1 };
		List<int[]> prelimStartCandidates = new ArrayList<int[]>();
		List<int[]> bestStartCandidates = new ArrayList<int[]>();

		for (int x = 0; x < world.getSizeX(); x++)
		{
			for (int y = 0; y < world.getSizeY(); y++)
			{
				if (!world.getTile(x, y).hasAgent())
				{
					int[] bestSCInfo = { -3, -3, -3 };
					bestSCInfo[0] = x;
					bestSCInfo[1] = y;
					bestSCInfo[2] = world.getTile(x, y).getAppeal();
					prelimStartCandidates.add(bestSCInfo);
				}
			}
		}
		// looping through all candidates (should be all tiles without agents at this point
		for (int i = 0; i < prelimStartCandidates.size(); i++)
		{
			if (prelimStartCandidates.get(i)[2] == 4) // if the appealrating is greater or equal to 4, these are the best candidates
			{
				bestStartCandidates.add(prelimStartCandidates.get(i)); // and thus they get added to the final list of candidates
			}
		}

		if (bestStartCandidates.size() == 0) // rather crudely- if none of the tiles have a 4 rating, we go down to 3
		{
			for (int i = 0; i < prelimStartCandidates.size(); i++)
			{
				if (prelimStartCandidates.get(i)[2] >= 3)
				{
					bestStartCandidates.add(prelimStartCandidates.get(i));
				}
			}

			if (bestStartCandidates.size() == 0) // rather crudely- if none of the tiles have a 4 rating, we go down to 3
			{
				for (int i = 0; i < prelimStartCandidates.size(); i++)
				{
					if (prelimStartCandidates.get(i)[2] >= 2)
					{
						bestStartCandidates.add(prelimStartCandidates.get(i));
					}
				}
				if (bestStartCandidates.size() == 0) // rather crudely- if none of the tiles have a 4 rating, we go down to 3
				{
					for (int i = 0; i < prelimStartCandidates.size(); i++)
					{
						if (prelimStartCandidates.get(i)[2] >= 1)
						{
							bestStartCandidates.add(prelimStartCandidates.get(i));
						}
					}
				}
				else
				{
					//print("<<collectBestStart>>Just sod it. Seriously. CAN'T FIND BEST STARTING POINT");
				}
			}
		}

		if (bestStartCandidates.size() == 1)
		{
			bestPos[0] = bestStartCandidates.get(0)[0];
			bestPos[1] = bestStartCandidates.get(0)[1];
		}

		else if (bestStartCandidates.size() > 1)
		{
			Collections.shuffle(bestStartCandidates); // tested & working 29 Sep 16
			bestPos[0] = bestStartCandidates.get(0)[0];
			bestPos[1] = bestStartCandidates.get(0)[1];
		}
		if (bestPos[0] != -1)
		{
			for (int u = 1; u < bestStartCandidates.size(); u++)
			{
				world.getTile(bestStartCandidates.get(u)[0], bestStartCandidates.get(u)[1]).isStartCandidate = true;
			}
			world.getTile(bestStartCandidates.get(0)[0], bestStartCandidates.get(0)[1]).isBestStart = true;
		}
		else
		{
			//print("<<collectBestStart>> bestPosition not found. " + bestPos[0] + ", " + bestPos[1]);
			Tile randomTile = emptyTiles.get(prng.nextInt(emptyTiles.size()));
			world.getTile(randomTile.getPosition()[0], randomTile.getPosition()[1]).isBestStart = true;
			bestPos[0] = randomTile.getPosition()[0];
			bestPos[1] = randomTile.getPosition()[1];
			//print("<<collectBestStart>> random Tile selected:" + bestPos[0] + ", " + bestPos[1]);

		}
		return bestPos;
	}
	
	/**Main method for placing migrants
	 * @param int[] pos**/
	public void findSpace(int[] pos)
	{
		if (influxPositions.size() < current_size)
		{
			// if the position does not have an agent
			if (world.getTile(pos[0], pos[1]).hasAgent() == false)
			{
				// and if the position is not already part of the influx
				if (world.getTile(pos[0], pos[1]).belongsToInflux == false)
				{

					// then we want that tile added to the influx:
					boolean moveOn = false;
					influxPositions.add(pos); // add tile to influx list
					world.setBelongsToInflux(pos[0], pos[1]); // tell the world the same thing
					moveOn = true;
					if (moveOn)
					{
						// here we get the positions of each neighbouring tile
						int[][] myLittleNeighbours = getNeighbourPositionsMoore(pos, 2);
						// shuffle the array so the agents don't all start with
						// the top left tile
						Collections.shuffle(Arrays.asList(myLittleNeighbours)); // shuffle tested & working 29 Sep 16

						// recursion! now the agent tries to find space to
						// settle at their most-appealing neighbour
						findSpace(checkMostAppealingNeighbour(pos));
					}
				}
				else
				{
					// what to do when the most appealing neighbour has already
					// influx? well.. better not add them in the first place
					print("<<findSpace>> " + pos[0] + ", " + pos[1] + " has no agent, but is part of the influx.");
				}
			}
			else
			{
				// the current tile already has an agent. so we use...
				// RECURSION! and move on to the next most appealing neighbour.
				findSpace(checkMostAppealingNeighbour(pos));
			}
		}
		else
		{
			// print("<<findSpace>> InfluxPositions.size() is not <= size. It's " +
			// influxPositions.size() + " and " + size);
		}
	}


	/**
	 * Method that oversees the placing of new agents.
	 * 1. Collect the best Start (call collectBestStart())
	 * 2. Flux going from best Start (calling flux())
	 * 
	 * @param fluxsize
	 **/
	public void placeAgents(int fluxsize)
	{
		Double influxsize = fluxsize * 1.0;
		if (countAgentsOnMap() >= influxsize) // check that the number of agents in total is greater or equal to fluxes.
		{
			influxPositions.clear();
			int[] bestPos = collectBestStart();
			flux(influxsize, bestPos[0], bestPos[1]); // number of agentsto be added, X and Y value of where the flux starts
		}
		else
		{
			print("<placeAgents>: " + allAgents.size() + " <= " + totalInfluxMax + "? Not enough empty space for remaining agents to be fluxed");
		}

		world.removeAllCandidateInfo();
	}

	/**
	 * The method that will go through all the tiles and that will call the
	 * floodfill on each tile
	 **/
	public void clusterSearch()
	{
		// so this is a list of lists. the first list contains the foundGroups.
		// the second list are the members of each group
		List<ArrayList<int[]>> foundGroups = new ArrayList<ArrayList<int[]>>();
		world.removeBelongsToGroupFlag();

		for (int x = 0; x < sizeX; x++)// looping through the world
		{
			for (int y = 0; y < sizeY; y++)
			{
				int currPos[] = { x, y };
				// we check if the tile has an agent AND whether that agent, if
				// exists, belongs to a group already.
				if (world.getTile(x, y).hasAgent() == true && world.getTile(x, y).belongsToGroup == false)
				{
					// if both conditions are satisfied, the floodfill results list (Position) is cleared
					floodFillResults.clear();
					floodfill(currPos, false); // here we call the floodfill method.

					if (floodFillResults.size() > 2) // Omitting empty lists here; minimum group size is THREE agents
					{
						foundGroups.add(new ArrayList<int[]>()); // add new list to the found group list. that list contains the agents

						for (int i = 0; i < floodFillResults.size(); i++)
						{
							// print("Group Member #" + i + ": " + floodFillResults.get(i)[0] + "," + floodFillResults.get(i)[1]);
							foundGroups.get(foundGroups.size() - 1).add(floodFillResults.get(i));
						}
					}
				}
			}
		}

	}

	/**
	 * Main method for implementing flood-fill.
	 * 
	 * @param Position
	 *            currentPosition (int x and int y)
	 * @param Boolean
	 *            searchNative (search for Natives yes or no)
	 **/
	public void floodfill(int[] pos, boolean searchBlue)
	{
		if (world.getTile(pos[0], pos[1]).hasAgent())// checking for agent to determine whether we need to flood-fill or not
		{
			boolean moveOn = false;
			if (searchBlue) // if searchBlue is enabled, search for blue groups
			{
				if (world.getTile(pos[0], pos[1]).isAgentBlue()) // check if agent is native
				{
					floodFillResults.add(pos); // if yes then add the position to the list of positions of flood-fill-results
					world.setBelongsToGroupFlag(pos[0], pos[1]); // the tile position will get the flag 'belongs to group'
					moveOn = true; // after thats done, we move on to the next tile
				}
			}
			else if (!world.getTile(pos[0], pos[1]).isAgentBlue()) // check if agent is green, i.e. go for green groups instead
			{
				floodFillResults.add(pos);
				world.setBelongsToGroupFlag(pos[0], pos[1]);
				moveOn = true;
			}

			if (moveOn) // if true
			{
				int[][] myLittleNeighbours = getNeighbourPositionsVNeu1(pos); // collect the neighbour positions: only strict < ^ > v

				for (int i = 0; i < myLittleNeighbours.length; i++) // loop through the neighbours
				{
					// we have stored the already searched positions in the
					// floodfillresults list, and here we check whether that
					// list contains any of the eight neighbour positions we
					// have just collected. one by one ofc.
					if (!world.getTile(myLittleNeighbours[i][0], myLittleNeighbours[i][1]).belongsToGroup)
					{
						floodfill(myLittleNeighbours[i], searchBlue);
						// if the neighbouring position has not previously been
						// added to the floodfill results, we perform floodfill
						// on that tile (recursion)
					}
				}
			}
		}
	}

	
	
	// --- small utilities ---
	
	// Função genérica que devolve vetor [count, mean, var] para UM grupo específico
	private double[] computeClusterStatsForGroup(int groupLabel, java.util.function.Function<Tile,Integer> getLabel, int radius) {
	    boolean[][] visited = new boolean[sizeX][sizeY];
	    java.util.ArrayList<Integer> sizes = new java.util.ArrayList<>();

	    for (int x = 0; x < sizeX; x++) {
	        for (int y = 0; y < sizeY; y++) {
	            Tile t = world.getTile(x, y);
	            if (!t.hasAgent() || visited[x][y]) continue;
	            int label = getLabel.apply(t);
	            if (label != groupLabel) continue;

	            // BFS
	            java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
	            q.add(new int[]{x, y});
	            visited[x][y] = true;
	            int clusterSize = 0;

	            while (!q.isEmpty()) {
	                int[] pos = q.poll();
	                int cx = pos[0], cy = pos[1];
	                clusterSize++;

	                int[][] neigh = getNeighbourPositionsMoore(new int[]{cx, cy}, radius);
	                for (int[] nb : neigh) {
	                    int nx = nb[0], ny = nb[1];
	                    if (visited[nx][ny]) continue;
	                    Tile n = world.getTile(nx, ny);
	                    if (!n.hasAgent()) continue;
	                    if (getLabel.apply(n) == groupLabel) {
	                        visited[nx][ny] = true;
	                        q.add(new int[]{nx, ny});
	                    }
	                }
	            }
	            if (clusterSize > 0) sizes.add(clusterSize);
	        }
	    }

	    // estatísticas simples
	    double count = sizes.size();
	    if (count == 0) return new double[]{0, 0, 0};

	    double sum = 0;
	    for (int s : sizes) sum += s;
	    double mean = sum / count;

	    double varSum = 0;
	    for (int s : sizes) varSum += Math.pow(s - mean, 2);
	    double var = varSum / count;

	    return new double[]{count, mean, var};
	}


	// Função que atualiza todos os grupos
	private void computeClustersByGroup() {
	    int radius = 1; // ou 2, conforme o teu modelo

	    // --- RAÇA ---
	    for (int g = 0; g < 2; g++) {
	        final int label = g;
	        clusterStatsRace[g] = computeClusterStatsForGroup(label, t -> (t.isAgentBlue() ? 1 : 0), radius);
	    }

	    // --- CLASSE SOCIAL ---
	    for (int g = 0; g < 3; g++) {
	        final int label = g;
	        clusterStatsClass[g] = computeClusterStatsForGroup(label, t -> t.getagentSocialClass(), radius);
	    }
	}
	
	
	private int wrap(int v, int max) { return ((v % max) + max) % max; }
	private double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
	// === Public one-liners you can call anywhere ===
	public double moransColour()    { return morans(Attr.COLOUR); }
	public double moransTolerance() { return morans(Attr.TOLERANCE); }
	public double moransClass()     { return morans(Attr.CLASS); }
	// Linear index consistent with w(i,j): k = x*sizeY + y
	private int lin(int x, int y) { return x * sizeY + y; }
	
	
	public int w(int i, int j) // takes two tiles and returns 1 if tiles are adjacent
	{
		// what distinguishes neighbouring tiles? They must be a maximum of one
		// row & one column away
		// so if rownumber OR columnumber difference is greater than 1 = can't
		// be adjacent
		// BUT the word wraps around so the column number has to be reset
		int isNB = 0;
		int ix = singleDimension(i)[0];
		int iy = singleDimension(i)[1];
		int jx = singleDimension(j)[0];
		int jy = singleDimension(j)[1];

		if (ix == sizeX - 1)
		{
			ix = ix - (sizeX - 1);
		}
		if (iy == sizeY - 1)
		{
			iy = iy - (sizeY - 1);
		}
		if (jx == sizeX - 1)
		{
			jx = jx - (sizeX - 1);
		}
		if (jy == sizeY - 1)
		{
			jy = jy - (sizeY - 1);
		}

		if (ix - jx > 1 || ix - jx < -1)
		{
			isNB = 0;
		}
		else if (iy - jy > 1 || iy - jy < -1)
		{
			isNB = 0;
		}
		else
		{
			isNB = 1;
		}
		return isNB;
	}

	public int[] singleDimension(int i) {
	    return new int[]{ i % sizeX, i / sizeY };
	}
	
	/**
	 * Method to generate a set of w unique numbers designed to help agents
	 * chose random empty tiles
	 **/
	public int[] generateUniqueNumbers(int w)
	{
		if (w > emptyTiles.size())
		{
			w = emptyTiles.size();
			// print("<<generateUniqueNumbers>> w is " + w + ", but empty Tiles remaining are " +
			// emptyTiles.size() + ". w = " + emptyTiles.size());
		}
		ArrayList<Integer> list = new ArrayList<Integer>();
		int[] listOfW = new int[w];
		for (int i = 0; i < emptyTiles.size(); i++)
		{
			list.add(new Integer(i));
		}
		Collections.shuffle(list, new Random(System.nanoTime()));

		for (int i = 0; i < w; i++)
		{
			listOfW[i] = list.get(i);
		}

		return listOfW;
	}
	

	
	// Private helper: inverse normal CDF (Abramowitz–Stegun)
	private static double inverseNormal(double p) {
	    if (p <= 0 || p >= 1)
	        throw new IllegalArgumentException("p must be in (0,1)");

	    double a1 = -39.69683028665376, a2 = 220.9460984245205, a3 = -275.9285104469687;
	    double a4 = 138.3577518672690, a5 = -30.66479806614716, a6 = 2.506628277459239;
	    double b1 = -54.47609879822406, b2 = 161.5858368580409;
	    double b3 = -155.6989798598866, b4 = 66.80131188771972, b5 = -13.28068155288572;
	    double c1 = -0.007784894002430293, c2 = -0.3223964580411365;
	    double c3 = -2.400758277161838, c4 = -2.549732539343734;
	    double c5 = 4.374664141464968, c6 = 2.938163982698783;
	    double d1 = 0.007784695709041462, d2 = 0.3224671290700398;
	    double d3 = 2.445134137142996, d4 = 3.754408661907416;
	    double q, r;

	    if (p < 0.02425) {
	        q = Math.sqrt(-2 * Math.log(p));
	        return (((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6)
	             / ((((d1 * q + d2) * q + d3) * q + d4) * q + 1);
	    } else if (p > 1 - 0.02425) {
	        q = Math.sqrt(-2 * Math.log(1 - p));
	        return -(((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6)
	              / ((((d1 * q + d2) * q + d3) * q + d4) * q + 1);
	    } else {
	        q = p - 0.5;
	        r = q * q;
	        return (((((a1 * r + a2) * r + a3) * r + a4) * r + a5) * r + a6) * q
	              / (((((b1 * r + b2) * r + b3) * r + b4) * r + b5) * r + 1);
	    }
	}
	
	


	// Weight function: ring 0 (center) → 1.0; ring 1 → 1/2; ring 2 → 1/3; ...
	private double ringWeight(int dx, int dy) {
	    int d = Math.max(Math.abs(dx), Math.abs(dy)); // Chebyshev distance
	    return 1.0 / (d + 1.0);
	}

	// If you want a different shape, e.g. inverse-square for outer rings:
	// return 1.0 / ((d + 1.0) * (d + 1.0));

	

	
	public double generateThreshold()
	{
		double thresh = -99;
		{
			Random rng = new Random();
			thresh = rng.nextDouble();
			thresh = thresh * 100;
			if (thresh < 0.0 || thresh > 100)
			{
				thresh = 50;
			}
		}
		return thresh;
	}
	

	// Core Moran's I (occupied cells only)
	private double moransI(double[] x, boolean[] occ) {
	    int N = x.length, n = 0;
	    double mean = 0.0;
	    for (int i = 0; i < N; i++) if (occ[i]) { mean += x[i]; n++; }
	    if (n == 0) return 0.0;
	    mean /= n;

	    double num = 0.0, den = 0.0, W = 0.0;
	    for (int i = 0; i < N; i++) {
	        if (!occ[i]) continue;
	        double di = x[i] - mean;
	        den += di * di;
	        for (int j = 0; j < N; j++) {
	            if (!occ[j]) continue;
	            double wij = w(i, j);          // ensure w(i,i)=0; neighbors=1; else=0
	            if (wij == 0.0) continue;
	            num += di * (x[j] - mean) * wij;
	            W   += wij;
	        }
	    }
	    if (den == 0.0 || W == 0.0) return 0.0;
	    return (n / W) * (num / den);
	}

	// Build one attribute vector + occupancy mask, then compute Moran's I
	private double morans(Attr which) {
	    final int N = sizeX * sizeY;
	    double[] x = new double[N];
	    boolean[] occ = new boolean[N];

	    for (int X = 0; X < sizeX; X++) {
	        for (int Y = 0; Y < sizeY; Y++) {
	            int k = lin(X, Y);
	            Tile t = world.getTile(X, Y);
	            if (!(occ[k] = t.hasAgent())) continue;

	            int[] pos = { X, Y };
	            Agent a = getAgent(pos); // use your existing helper

	            switch (which) {
	                case COLOUR:
	                    x[k] = t.isAgentBlue() ? 1.0 : 0.0;               // 1=Blue, 0=Green
	                    break;
	                case TOLERANCE:
	                    x[k] = (a != null) ? a.getThreshold() : t.getT(); // read from Agent
	                    break;
	                case CLASS:
	                    x[k] = (a != null) ? a.getSocialClass() : 0.0;    // 0,1,2
	                    break;
	            }
	        }
	    }
	    return moransI(x, occ);
	}
	
	// Pearson corr: RENT (X) vs RACE (Y: 0=Green, 1=Blue), overall
	private double pearsonPriceRace() {
	    double sumX=0, sumY=0, sumXX=0, sumYY=0, sumXY=0;
	    int n = 0;
	    for (int x = 0; x < sizeX; x++) {
	        for (int y = 0; y < sizeY; y++) {
	            Tile t = world.getTile(x, y);
	            if (!t.hasAgent()) continue;
	            double X = t.getRent();
	            double Y = t.isAgentBlue() ? 1.0 : 0.0;
	            n++;
	            sumX += X; sumY += Y;
	            sumXX += X*X; sumYY += Y*Y; sumXY += X*Y;
	        }
	    }
	    if (n < 2) return 0.0;
	    double num = n*sumXY - sumX*sumY;
	    double den = Math.sqrt((n*sumXX - sumX*sumX) * (n*sumYY - sumY*sumY));
	    if (den == 0.0) return 0.0;
	    return num / den;
	}
	
	// Correlação de Pearson entre preço (rent) e classe (0,1,2) nas células ocupadas
	private double pearsonPriceClass() {
	    double sumX = 0, sumY = 0, sumXX = 0, sumYY = 0, sumXY = 0;
	    int n = 0;

	    for (int x = 0; x < sizeX; x++) for (int y = 0; y < sizeY; y++) {
	        Tile t = world.getTile(x, y);
	        if (!t.hasAgent()) continue;
	        double X = t.getRent();
	        double Y = t.getagentSocialClass(); // 0,1,2
	        n++;
	        sumX += X; sumY += Y;
	        sumXX += X * X; sumYY += Y * Y; sumXY += X * Y;
	    }
	    if (n < 2) return 0.0;
	    double num = n * sumXY - sumX * sumY;
	    double den = Math.sqrt((n * sumXX - sumX * sumX) * (n * sumYY - sumY * sumY));
	    if (den == 0) return 0.0;
	    return num / den;
	}






	private void clearAccumulators() {
	    // zero all private accumulators and public outputs
	    for (int r = 0; r < RACES; r++) {
	        _countsByRace[r] = 0; _happyByRace[r] = 0; _tolSumByRace[r] = 0.0; _affordSumByRace[r] = 0.0;
	        countsByRace[r] = 0; happyRateByRace[r] = 0.0; tolMeanByRace[r] = 0.0; 
	        for (int c = 0; c < CLASSES; c++) {
	            _countsByRaceClass[r][c] = 0;
	            _happyCountsByRaceClass[r][c] = 0;
	            _tolSumByRaceClass[r][c] = 0.0;
	            _affordSumByRaceClass[r][c] = 0.0;

	            countsByRaceClass[r][c] = 0;
	            happyRateByRaceClass[r][c] = 0.0;
	            tolMeanByRaceClass[r][c] = 0.0;
	        }
	    }
	    for (int c = 0; c < CLASSES; c++) {
	        _countsByClass[c] = 0; _happyByClass[c] = 0; _tolSumByClass[c] = 0.0; _affordSumByClass[c] = 0.0;
	        countsByClass[c] = 0; happyRateByClass[c] = 0.0; tolMeanByClass[c] = 0.0; 
	    }
	    affordBelowCountAll = 0;
	    for (int r = 0; r < RACES; r++) affordBelowByRace[r] = 0;
	    for (int c = 0; c < CLASSES; c++) affordBelowByClass[c] = 0;

	}
	
	/**
	 * This is the method that creates a list. A LIST. OF EMPTY TILES.
	 */
	public void collectEmptyTiles()
	{
		emptyTiles.clear();
		for (int x = 0; x < world.getSizeX(); x++)
		{
			for (int y = 0; y < world.getSizeY(); y++)
			{
				Tile curT = world.getTile(x, y);

				if (!curT.hasAgent())
				{
					emptyTiles.add(curT);
				}
			}
		}
	}
	
	
	public List<DescriptiveStatistics> collectMoreTolData()
	{
		List<DescriptiveStatistics> returnList = new ArrayList<>();
		DescriptiveStatistics tolstats = new DescriptiveStatistics();
		List<Double> toldata = collectThresholdALL();
		for (int t = 0; t < toldata.size(); t++)
		{
			tolstats.addValue(toldata.get(t));
		}

		DescriptiveStatistics natTolStats = new DescriptiveStatistics();
		List<Double> ntoldata = collectThresholdNat();
		for (int n = 0; n < ntoldata.size(); n++)
		{
			natTolStats.addValue(ntoldata.get(n));
		}

		DescriptiveStatistics migTolStats = new DescriptiveStatistics();
		List<Double> mtoldata = collectThresholdMig();
		for (int m = 0; m < mtoldata.size(); m++)
		{
			migTolStats.addValue(mtoldata.get(m));
		}
		returnList.add(tolstats);
		returnList.add(natTolStats);
		returnList.add(migTolStats);

		return returnList;
	}


	public List<Double> collectThresholdALL()
	{
		List<Double> allThresholds = new ArrayList<Double>();
		for (int i = 0; i < allAgents.size(); i++)
		{
			allThresholds.add(allAgents.get(i).getThreshold());
		}
		return allThresholds;
	}

	public List<Double> collectThresholdNat()
	{
		List<Double> natThresholds = new ArrayList<Double>();
		for (int i = 0; i < allAgents.size(); i++)
		{
			if (!allAgents.get(i).isBlue)
			{
				natThresholds.add(allAgents.get(i).getThreshold());
			}
		}
		return natThresholds;
	}

	public List<Double> collectThresholdMig()
	{
		List<Double> migThresholds = new ArrayList<Double>();
		for (int i = 0; i < allAgents.size(); i++)
		{
			if (allAgents.get(i).isBlue)
			{
				migThresholds.add(allAgents.get(i).getThreshold());
			}
		}
		return migThresholds;
	}

	
	public int[] collectPopulationData() {
	    df2.setRoundingMode(RoundingMode.HALF_EVEN);

	    // Reset legacy globals
	    int[] happiness = {0, 0};   // [unhappy, happy]
	    int[] bluehappy = {0, 0};
	    int[] greenhappy = {0, 0};
	    double[] avgTolRace = {0.0, 0.0}; // blue, green
	    population[0] = 0; population[1] = 0;

	    // Reset new accumulators and outputs
	    clearAccumulators();

	    // Walk the grid
	    for (int x = 0; x < world.getSizeX(); x++) {
	        for (int y = 0; y < world.getSizeY(); y++) {
	            Tile t = world.getTile(x, y);
	            if (!t.hasAgent()) continue;

	            boolean isBlue = t.isAgentBlue();
	            int race = isBlue ? 0 : 1;
	            int cls  = t.getagentSocialClass();

	            double tol     = t.getT();                 // tolerance ∈ [0,1]
	            double income  = t.getagentRichness();
	            double rent    = t.getRent();              // rename getter if needed
	            double afford  = clamp01(affordabilityScore(rent, income));
	            boolean happy  = (t.isAgentHappy() == 1);
	            
	            if (afford < AFFORDABILITY) {            // AFFORDABILITY == 0.3 in your code
	                affordBelowCountAll++;
	                affordBelowByRace[race]++;
	                affordBelowByClass[cls]++;
	            }
	            

	            // Legacy globals
	            if (happy) happiness[1]++; else happiness[0]++;
	            if (isBlue) {
	                avgTolRace[0] += tol;
	                population[0]++;
	                if (happy) bluehappy[1]++; else bluehappy[0]++;
	            } else {
	                avgTolRace[1] += tol;
	                population[1]++;
	                if (happy) greenhappy[1]++; else greenhappy[0]++;
	            }

	            // ---- Per (race × class) accumulators
	            _countsByRaceClass[race][cls]++;
	            _tolSumByRaceClass[race][cls]    += tol;
	            _affordSumByRaceClass[race][cls] += afford;
	            if (happy) _happyCountsByRaceClass[race][cls]++;

	            // ---- Collapsed by race
	            _countsByRace[race]++;
	            _tolSumByRace[race]    += tol;
	            _affordSumByRace[race] += afford;
	            if (happy) _happyByRace[race]++;

	            // ---- Collapsed by class
	            _countsByClass[cls]++;
	            _tolSumByClass[cls]    += tol;
	            _affordSumByClass[cls] += afford;
	            if (happy) _happyByClass[cls]++;
	        }
	    }

	    // Derive globals (means/rates)
	    if (allAgents.size() > 0) {
	        numBlues  = population[0];
	        numGreens = population[1];

	        averageTB = (numBlues  > 0) ? (avgTolRace[0] / numBlues)  : 0.0;
	        averageTG = (numGreens > 0) ? (avgTolRace[1] / numGreens) : 0.0;

	        globalHappiness  = (double) happiness[1] / allAgents.size();

	        meanBlue         = (double) numBlues / allAgents.size();
	        
	     // ---- Global tolerance (mean over all agents)
	        int totalCount = _countsByRace[0] + _countsByRace[1];
	        double totalTol   = _tolSumByRace[0] + _tolSumByRace[1];
	        globalTolerance   = (totalCount > 0) ? clamp01(totalTol / (double) totalCount): 0.0;

	        // ---- Global affordability (mean over all agents)
	        double totalAfford = _affordSumByRace[0] + _affordSumByRace[1];


	        globalAffordability = (totalCount > 0) ? clamp01(totalAfford / (double) totalCount): 0.0;

	        // Publish per (race × class)
	        for (int r = 0; r < RACES; r++) {
	            for (int c = 0; c < CLASSES; c++) {
	                int n = _countsByRaceClass[r][c];
	                countsByRaceClass[r][c] = n;
	                if (n > 0) {
	                    happyRateByRaceClass[r][c]  = clamp01((double) _happyCountsByRaceClass[r][c] / n);
	                    tolMeanByRaceClass[r][c]    = (_tolSumByRaceClass[r][c] / n);
	                } else {
	                    happyRateByRaceClass[r][c] = 0.0;
	                    tolMeanByRaceClass[r][c]   = 0.0;
	                }
	            }
	        }

	        // Publish collapsed by race
	        for (int r = 0; r < RACES; r++) {
	            int n = _countsByRace[r];
	            countsByRace[r] = n;
	            if (n > 0) {
	                happyRateByRace[r] = clamp01((double) _happyByRace[r] / n);
	                tolMeanByRace[r]   = (_tolSumByRace[r] / n);
	            } else {
	                happyRateByRace[r] = 0.0;
	                tolMeanByRace[r]   = 0.0;
	            }
	        }

	        // Publish collapsed by class
	        for (int c = 0; c < CLASSES; c++) {
	            int n = _countsByClass[c];
	            countsByClass[c] = n;

	            if (n > 0) {
	                happyRateByClass[c]  = clamp01((double) _happyByClass[c] / n);
	                tolMeanByClass[c]    = (_tolSumByClass[c] / n);
	            } else {
	                happyRateByClass[c]  = 0.0;
	                tolMeanByClass[c]    = 0.0;
	            }
	        }
	    }

	    return population; 
	}


	
	
	public void collectData() {
	    // 1) Populate all globals (legacy + new per-race/class)
	    collectPopulationData();
	 // === NOVAS MÉTRICAS DE CLUSTERS ===
	    computeClustersByGroup();
	    priceClassCorr = pearsonPriceClass();
	    priceRaceCorr = pearsonPriceRace();
	    
	    
	 // === Split averages: AvgChoicesPerUnhappy by race/class ===
	    for (int r = 0; r < 2; r++) {
	        avgChoicesPerUnhappyByRace[r] = (unhappyCheckedByRace[r] > 0)
	                ? (double) totalChoicesByRace[r] / (double) unhappyCheckedByRace[r]
	                : 0.0;
	    }
	    for (int c = 0; c < 3; c++) {
	        avgChoicesPerUnhappyByClass[c] = (unhappyCheckedByClass[c] > 0)
	                ? (double) totalChoicesByClass[c] / (double) unhappyCheckedByClass[c]
	                : 0.0;
	    }


	    // 2) Tolerance descriptive stats (legacy)
	    List<DescriptiveStatistics> allTdata = collectMoreTolData();
	    DescriptiveStatistics tolstats  = allTdata.get(0);
	    DescriptiveStatistics ntolstats = allTdata.get(1);
	    DescriptiveStatistics mtolstats = allTdata.get(2);

	    // 3) Moran’s I (color) and Moran’s T (tolerance)
	    double morans   = moransColour();
	    double moranTol = moransTolerance();
	    double moranSocialClass = moransClass();
	    
	    String priceClassCorrStr = String.valueOf(priceClassCorr);
	    String priceRaceCorrStr  = String.valueOf(priceRaceCorr);
	    mC = morans;
	    mT = moranTol;

	    // 5) Prepare strings (legacy 28 columns first, unchanged order)
	    String moranStr        = String.valueOf(morans);
	    String moranTolStr     = String.valueOf(moranTol);
	    String moranClassStr   = String.valueOf(moranSocialClass);
	    String globalHappyStr  = String.valueOf(globalHappiness);
	    String numAgentStr     = String.valueOf(allAgents.size());
	    String totalMoveStr    = String.valueOf(globalMoveCounter);
	    String changedMindStr  = String.valueOf(changedMind);

	    String tolN        = String.valueOf(tolstats.getN());
	    String tolmean     = String.valueOf(tolstats.getMean());
	    String tolvar      = String.valueOf(tolstats.getVariance());
	    String tolstdDev   = String.valueOf(tolstats.getStandardDeviation());
	    String tolKurt     = String.valueOf(tolstats.getKurtosis());
	    String tolSkew     = String.valueOf(tolstats.getSkewness());

	    String ntolN       = String.valueOf(ntolstats.getN());
	    String ntolmean    = String.valueOf(ntolstats.getMean());
	    String ntolvar     = String.valueOf(ntolstats.getVariance());
	    String ntolstdDev  = String.valueOf(ntolstats.getStandardDeviation());
	    String ntolKurt    = String.valueOf(ntolstats.getKurtosis());
	    String ntolSkew    = String.valueOf(ntolstats.getSkewness());

	    String mtolN       = String.valueOf(mtolstats.getN());
	    String mtolmean    = String.valueOf(mtolstats.getMean());
	    String mtolvar     = String.valueOf(mtolstats.getVariance());
	    String mtolstdDev  = String.valueOf(mtolstats.getStandardDeviation());
	    String mtolKurt    = String.valueOf(mtolstats.getKurtosis());
	    String mtolSkew    = String.valueOf(mtolstats.getSkewness());
	    
	    String affBelowAll   = String.valueOf((double)affordBelowCountAll);
	    String affBelowBlue  = String.valueOf((double)affordBelowByRace[0]/(double)countsByRace[0]);
	    String affBelowGreen = String.valueOf((double)affordBelowByRace[1]/(double)countsByRace[1]);
	    String affBelowC0    = String.valueOf((double)affordBelowByClass[0]/(double)countsByClass[0]);
	    String affBelowC1    = String.valueOf((double)affordBelowByClass[1]/(double)countsByClass[1]);
	    String affBelowC2    = String.valueOf((double)affordBelowByClass[2]/(double)countsByClass[2]);


	    // 6) Append NEW fields — stable wide schema
	    // By race (0=Blue, 1=Green) H-Happiness T-Tolerance A-Affordability
	    String r0_count = String.valueOf(countsByRace[0]);
	    String r1_count = String.valueOf(countsByRace[1]);
	    String r0_H = String.valueOf(happyRateByRace[0]);
	    String r1_H = String.valueOf(happyRateByRace[1]);
	    String r0_T = String.valueOf(tolMeanByRace[0]);
	    String r1_T = String.valueOf(tolMeanByRace[1]);
	    

	    // By class (0,1,2)
	    String c0_count = String.valueOf(countsByClass[0]);
	    String c1_count = String.valueOf(countsByClass[1]);
	    String c2_count = String.valueOf(countsByClass[2]);
	    String c0_H = String.valueOf(happyRateByClass[0]);
	    String c1_H = String.valueOf(happyRateByClass[1]);
	    String c2_H = String.valueOf(happyRateByClass[2]);
	    String c0_T = String.valueOf(tolMeanByClass[0]);
	    String c1_T = String.valueOf(tolMeanByClass[1]);
	    String c2_T = String.valueOf(tolMeanByClass[2]);

	    
		 // --- Converter em Strings (igual a mtolstats) ---
	
		 // RAÇA: 0=verde, 1=azul
		 String raceGreenN    = String.valueOf(clusterStatsRace[0][0]);
		 String raceGreenMean = String.valueOf(clusterStatsRace[0][1]);
		 String raceGreenVar  = String.valueOf(clusterStatsRace[0][2]);
	
		 String raceBlueN     = String.valueOf(clusterStatsRace[1][0]);
		 String raceBlueMean  = String.valueOf(clusterStatsRace[1][1]);
		 String raceBlueVar   = String.valueOf(clusterStatsRace[1][2]);
	
		 // CLASSE: 0=pobre, 1=média, 2=rica
		 String classPoorN    = String.valueOf(clusterStatsClass[0][0]);
		 String classPoorMean = String.valueOf(clusterStatsClass[0][1]);
		 String classPoorVar  = String.valueOf(clusterStatsClass[0][2]);
	
		 String classMidN     = String.valueOf(clusterStatsClass[1][0]);
		 String classMidMean  = String.valueOf(clusterStatsClass[1][1]);
		 String classMidVar   = String.valueOf(clusterStatsClass[1][2]);
	
		 String classRichN    = String.valueOf(clusterStatsClass[2][0]);
		 String classRichMean = String.valueOf(clusterStatsClass[2][1]);
		 String classRichVar  = String.valueOf(clusterStatsClass[2][2]);
		 
		 
		// --- Split metrics to strings (race: 0=Green,1=Blue) ---
		 String failGreen = String.valueOf(failedMoveAttemptsByRace[0]);
		 String failBlue  = String.valueOf(failedMoveAttemptsByRace[1]);
		 String avgChGreen = String.valueOf(avgChoicesPerUnhappyByRace[0]);
		 String avgChBlue  = String.valueOf(avgChoicesPerUnhappyByRace[1]);
	

		 // --- Split metrics to strings (class: 0=Poor,1=Mid,2=Rich) ---
		 String failC0 = String.valueOf(failedMoveAttemptsByClass[0]);
		 String failC1 = String.valueOf(failedMoveAttemptsByClass[1]);
		 String failC2 = String.valueOf(failedMoveAttemptsByClass[2]);

		 String avgChC0 = String.valueOf(avgChoicesPerUnhappyByClass[0]);
		 String avgChC1 = String.valueOf(avgChoicesPerUnhappyByClass[1]);
		 String avgChC2 = String.valueOf(avgChoicesPerUnhappyByClass[2]);

	    

		// -- write CSV header once (must match theString order exactly) --
		 if (!csvHeaderWritten) {
		     String[] header = new String[] {
		         // --- Legacy 25 (your current order) ---
		         "MoranI_Color", "MoranI_Tolerance", "MoranI_Class",
		         "TotalMoves", "GlobalHappiness", "NumAgents", "ChangedMind",
		         "Tol_N", "Tol_Mean", "Tol_Var", "Tol_StdDev", "Tol_Kurtosis", "Tol_Skewness",
		         "NTol_N", "NTol_Mean", "NTol_Var", "NTol_StdDev", "NTol_Kurtosis", "NTol_Skewness",
		         "MTol_N", "MTol_Mean", "MTol_Var", "MTol_StdDev", "MTol_Kurtosis", "MTol_Skewness",

		         // --- By race (0=Blue, 1=Green) ---
		         "Blue_Count", "Green_Count",
		         "Blue_Happy", "Green_Happy",
		         "Blue_Tolerance", "Green_Tolerance",


		         // --- By class (0,1,2) ---
		         "Class0_Count", "Class1_Count", "Class2_Count",
		         "Class0_Happy", "Class1_Happy", "Class2_Happy",
		         "Class0_Tolerance", "Class1_Tolerance", "Class2_Tolerance",


		         // --- Affordability thresholds (shares) ---
		         "AffBelow_All", "AffBelow_Blue", "AffBelow_Green",
		         "AffBelow_Class0", "AffBelow_Class1", "AffBelow_Class2",

		         // --- Clusters by Race (index 0=Green, 1=Blue in your arrays) ---
		         "Cluster_Green_N", "Cluster_Green_Mean", "Cluster_Green_Var",
		         "Cluster_Blue_N",  "Cluster_Blue_Mean",  "Cluster_Blue_Var",

		         // --- Clusters by Class (0=Poor,1=Mid,2=Rich) ---
		         "Cluster_Class0_Poor_N", "Cluster_Class0_Poor_Mean", "Cluster_Class0_Poor_Var",
		         "Cluster_Class1_Mid_N",  "Cluster_Class1_Mid_Mean",  "Cluster_Class1_Mid_Var",
		         "Cluster_Class2_Rich_N", "Cluster_Class2_Rich_Mean", "Cluster_Class2_Rich_Var",

		         // --- Other metrics ---
		      // --- Split "FailedMoveAttempts" / "AvgChoicesPerUnhappy" / "PriceClassCorr" (by race) ---
		         "FailedMoveAttempts_Green", "FailedMoveAttempts_Blue",
		         "AvgChoicesPerUnhappy_Green", "AvgChoicesPerUnhappy_Blue",
		        

		         // --- Split by class (0=Poor,1=Mid,2=Rich) ---
		         "FailedMoveAttempts_Class0_Poor", "FailedMoveAttempts_Class1_Mid", "FailedMoveAttempts_Class2_Rich",
		         "AvgChoicesPerUnhappy_Class0_Poor", "AvgChoicesPerUnhappy_Class1_Mid", "AvgChoicesPerUnhappy_Class2_Rich",
		        
		         "PriceClassCorr", "PriceRaceCorr",
		     };
		     writer.writeNext(header);
		     csvHeaderWritten = true;
		 }

	    // 7) Build the full output row:
	   
	    String[] theString = new String[] {
	        // --- Legacy 28 ---
	        moranStr, moranTolStr, moranClassStr, totalMoveStr, globalHappyStr,
	        numAgentStr, changedMindStr,
	        tolN, tolmean, tolvar, tolstdDev, tolKurt, tolSkew,
	        ntolN, ntolmean, ntolvar, ntolstdDev, ntolKurt, ntolSkew,
	        mtolN, mtolmean, mtolvar, mtolstdDev, mtolKurt, mtolSkew,

	        // --- By race (Blue, Green) ---
	        r0_count, r1_count, r0_H, r1_H, r0_T, r1_T,

	        // --- By class (0,1,2) ---
	        c0_count, c1_count, c2_count,
	        c0_H, c1_H, c2_H,
	        c0_T, c1_T, c2_T,

	        
	        affBelowAll, affBelowBlue, affBelowGreen, affBelowC0, affBelowC1, affBelowC2,
	        
	     // ======= CLUSTERS POR RAÇA =======
	        raceGreenN, raceGreenMean, raceGreenVar,
	        raceBlueN,  raceBlueMean,  raceBlueVar,

	        // ======= CLUSTERS POR CLASSE SOCIAL =======
	        classPoorN, classPoorMean, classPoorVar,
	        classMidN,  classMidMean,  classMidVar,
	        classRichN, classRichMean, classRichVar,

	
	     
	        failGreen, failBlue,
	        avgChGreen, avgChBlue,
	  
	        failC0, failC1, failC2,
	        avgChC0, avgChC1, avgChC2,


	        priceClassCorrStr,               // overall class–price
	        priceRaceCorrStr,                 // overall race–price

	    };
	    // 8) Write row
	    writer.writeNext(theString);
	}
	
	
	public void run(int experimentNo)
	{
		int oldCM = 0;
		fireEvent(new SimulationStartedEvent(experimentNo, w, m, NUM_FLUXES, current_size));
		csv(experimentNo);
		for (tick = 0; tick < maxTicks; tick++)
		{
			oldCM = changedMind;
			changedMind = 0;
			globalMoveCounter = 0.0;
			prefcMcounter = 0;
			// NEW: reset split counters
			java.util.Arrays.fill(failedMoveAttemptsByRace,  0);
			java.util.Arrays.fill(unhappyCheckedByRace,      0);
			java.util.Arrays.fill(totalChoicesByRace,        0);

			java.util.Arrays.fill(failedMoveAttemptsByClass, 0);
			java.util.Arrays.fill(unhappyCheckedByClass,     0);
			java.util.Arrays.fill(totalChoicesByClass,       0);

			if (influx == 1 && fluxcounter < NUM_FLUXES) // after x ticks, influx
			{
				if (TICKS_OF_FLUX.get(fluxcounter)[0] == tick)
				{
					current_size = TICKS_OF_FLUX.get(fluxcounter)[1];
					if (w > emptyTiles.size())
					{
						// print("<<run>> w is larger than emptyTiles.size(): " + w + "> " + emptyTiles.size());
						w = emptyTiles.size();
						placeAgents(current_size);
						
						fluxcounter++;
					}
					else
					{
						placeAgents(current_size);
						
						fluxcounter++;
					}
				}
			}

			collectEmptyTiles();

			long seed = System.nanoTime(); // agents get shuffled every tick to avoid dis/advantages
			Collections.shuffle(allAgents, new Random(seed));

			invokeAgentActions();

			if ((tick % SAMPLING_INTERVAL) == 0)// after set amount of ticks: every 10 (if x divisible yields a 0)
			{
				collectData();
			}
			try
			{
				pauser.look();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			
			updateRent();
			updateAppeal();
			countAppealData();
			//print("<<run>> Changed preferences this tick ("+tick+"): " + prefcMcounter);
			fireEvent(new TickEnd(tick, averageTB, averageTG, globalHappiness, mC, mT));
		}
		fireEvent(new SimulationFinishedEvent());

		try
		{
			writer.flush();
			writer.close();
		}
		catch (IOException e)
		{
			print("Error: Could not close csv file.");
			throw new RuntimeException(e);
		}

		//since the simulation ended, we collect the final tolerance
		csvTolerance();
	}
	
	
	/**
	 * ~~HOW TO TOGGLE OVERIDE~~<br>
	 * To override: comment out Files.createFile(pathToFile); and activate the
	 * boo variable + if statement.<br>
	 * To stop override: activate the Files.createFile(pathToFile); and comment
	 * out the boo + if statement.<br>
	 * Method for dealing with the creation of the csv files and directories<br>
	 * First create the name of the file, generated using moveRule, tick,
	 * maxtick, agents, percentage<br>
	 * Then we try to create the path to the file<br>
	 * Then check if file exists<br>
	 * If yes, delete existing file<br>
	 * and create a new file in the same spot
	 **/
	public void csv(int experimentNo)
	{
		df0.setRoundingMode(RoundingMode.HALF_EVEN);
		String influxString = "";

		if (influx == 1)
		{
			influxString = "ON" + NUM_FLUXES + "z" + current_size;
		}
		else
		{
			influxString = "OFF";
		}
		Random r  = new Random();
		String longNameRan = "gr" + g + "fd" + fd + influxString + "w" + w + "m" + df4.format(m) + "ngini" + nativeGINI + "mgini" + migrantGINI + "c" +r.nextInt();
		csvname = longNameRan;
		String path = "output/";
		String csvName = path + longNameRan + ".csv";
		// String csvName = path + experimentNo + longNameRan + ".csv";
		currentDirectory = path + longNameRan;
		try
		{
			Path pathToFile = Paths.get(path + longNameRan + ".csv");
			// Path pathToFile = Paths.get(path + experimentNo + longNameRan + ".csv");
			Files.createDirectories(pathToFile.getParent());
			File f = new File(csvName);
			//boolean boo = f.createNewFile(); // check if file exists: if boolean
											// is false, file already exists
			//if (boo == false)
			//{
			//	// f.delete(); //boolean is false so we delete the existing file...
			//	f = new File(csvName + 1); // ...so that we can write a new one instead
			//}

			Files.createFile(pathToFile);
			writer = new CSVWriter(new FileWriter(csvName, true), ','); // once all files and directories are dealt with, we create the filewriter
			csvHeaderWritten = false;
		}
		catch (IOException e)
		{
			print("<csv>: Could not open csv file \"" + csvName + "\" for writing.");
			print(e.getMessage());
			System.exit(-2);
		}
	}

	/** 
	 * Method for saving the tolerance levels of all agents at the end of each simulation.
	 * Because the csv has between 2000 and 2495 columns, it will be saved into a
	 * separate file in a separate folder, named the same + the affix "_tol". */
	public void csvTolerance()
	{
		List<Double> data = collectThresholdALL();
		String[] strdata = new String[data.size()];
		for (int i = 0; i < data.size(); i++)
		{
			strdata[i] = "" + data.get(i);
		}

		String path = "output/";
		String currFileName = path + csvname + "_tol.csv";
		currentDirectory = path + csvname + "_tol";
		try
		{
			Path pathToFile = Paths.get(path + csvname + ".csv");
			Files.createDirectories(pathToFile.getParent());
			File f = new File(currFileName);
			boolean boo = f.createNewFile(); // check if file exists: if boolean is false, file already exists
			if (boo == false)
			{
				// f.delete(); //boolean is false so we delete the existing file...
				f = new File(currFileName + 1); // ...so that we can write a new one instead
			}

			// Files.createFile(pathToFile);

			writer = new CSVWriter(new FileWriter(currFileName, true), ',');
			String[] wrap = new String[1]; //probably saving on GC
			for (String s : strdata)
			{
				wrap[0] = s;
				writer.writeNext(wrap);
			}
		}

		catch (IOException e)
		{
			print("<csvTolerance>: Could not open csv file \"" + currFileName + "\" for writing.");
			print(e.getMessage());
			System.exit(-2);
		}
		try
		{
			writer.close();
		}
		catch (IOException e)
		{
			print("<csvTolerance>: Could not close csv file.");
			throw new RuntimeException(e);
		}

	}

	
	public Integer getTick()
	{
		return tick;
	}


	public World getWorld()
	{
		return world;
	}

	public Simulation getSimulation()
	{
		return this;
	}

	public Agent getAgent(int[] posWeWant)
	{
		Agent ourAgent = null;

		for (int i = 0; i < allAgents.size(); i++)
		{
			if (allAgents.get(i).pos[0] == posWeWant[0] && allAgents.get(i).pos[1] == posWeWant[1])
			{
				ourAgent = allAgents.get(i);
				break;
			}
		}

		return ourAgent;
	}

	public Grid getGrid()
	{
		return grid;
	}

	static <T> void print(T p)
	{
		System.out.println(p);
	}
	
	public void fireEvent(Object event)
	{
		setChanged();
		notifyObservers(event);
	}
	
	
	
	@Override
	public void update(Observable o, Object arg)
	{
	}

	@Override
	public void run()
	{
	}
}

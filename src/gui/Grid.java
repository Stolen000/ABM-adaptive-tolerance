package gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.EventListener;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JPanel;

import migscape.Tile;
import migscape.World;

/**
 * Migrationscape version 2.2
 * A version of the Schelling segregation model with adaptive tolerance.
 * Author: Linda Urselmans
 * University of Essex
 *  **/
public class Grid extends JPanel implements Observer, EventListener
{
	World world;
	int rectWidth;
	int rectHeight;
	int x;
	int y;
	boolean viewF = true; // true = show blue/green; false = show red/yellow
	boolean viewShadesOfF = true; // if true, show 5 levels of tolerance values
	int[] red5 = { 189, 0, 38 };
	int[] red4 = { 240, 59, 32 };
	int[] red3 = { 253, 141, 60 };
	int[] red2 = { 254, 204, 92 };
	int[] red1 = { 255, 255, 178 };
	
	// Add this for multiple views
	public enum ViewMode { COLOR, SEGREGATION, RICHNESS }
	ViewMode viewMode = ViewMode.COLOR;

	public void setViewMode(ViewMode mode) {
	    this.viewMode = mode;
	    repaint();
	}

	public Grid(World startWorld, boolean viewFTog)
	{
		this.world = startWorld;
		this.viewF = viewFTog;
	}

	public void updateTileinfo(World currentWorld)
	{
		this.world = currentWorld;;
	}

	public void setViewMode(boolean newShowPrivate)
	{
		viewF = newShowPrivate;
	}

	public boolean getViewMode()
	{
		return viewF;
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension d = new Dimension(world.getSizeX() * 10, world.getSizeY() * 10);
		return d;
	}

	@Override
	public void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		// Clear the board
		g.clearRect(0, 0, getWidth(), getHeight());
		// Draw the grid
		rectWidth = getWidth() / world.getSizeX();
		rectHeight = getHeight() / world.getSizeY();

		for (int i = 0; i < world.getSizeX(); i++)
		{
			for (int j = 0; j < world.getSizeY(); j++)
			{
				// Upper left corner of this terrain rect
				x = i * rectWidth;
				y = j * rectHeight;
				Tile currTile = world.getTile(i, j);

				if (currTile.hasAgent()) {
				    switch(viewMode) {
				        case COLOR:
				            if (currTile.isAgentBlue()) g.setColor(new Color(45,45,212));
				            else g.setColor(new Color(133,231,19));
				            break;
				        case SEGREGATION:
				            double tol = currTile.getT();
				            if (tol < 20) g.setColor(new Color(red1[0],red1[1],red1[2]));
				            else if (tol < 40) g.setColor(new Color(red2[0],red2[1],red2[2]));
				            else if (tol < 60) g.setColor(new Color(red3[0],red3[1],red3[2]));
				            else if (tol < 80) g.setColor(new Color(red4[0],red4[1],red4[2]));
				            else g.setColor(new Color(red5[0],red5[1],red5[2]));
				            break;
				        case RICHNESS:
				            if (currTile.hasAgent()) {
				                // Normalize money to 0–1
				                double moneyNorm = currTile.getagentRichness() / 1000.0;

				                // Apply exponential scaling
				                double moneyExp = Math.pow(moneyNorm, 3);

				                // Convert to intensity (cap at 200 to avoid red)
				                int intensity = (int) Math.round(moneyExp * 200);
				                intensity = Math.max(0, Math.min(200, intensity));

				                // Yellow gradient
				                g.setColor(new Color(255, 255 - intensity, 0));
				            } else {
				                g.setColor(Color.white);
				            }
				            break;


				    }
				}

				else if (currTile.isBestStart)
				{
					g.setColor(new Color(85, 26, 139)); //Purple #551A8B
				}
				else if (currTile.isStartCandidate)
				{
					g.setColor(Color.orange);
				}

				else //empty tile
				{
					g.setClip(getVisibleRect());
					if (viewShadesOfF && viewF)
					{
						g.setColor(Color.LIGHT_GRAY);
					}
					else
					{
						g.setColor(Color.white);
					}
				}
				if (currTile.belongsToInflux == true)
				{
					g.setColor(Color.cyan);
				}
				g.fillRect(x, y, rectWidth, rectHeight);

			}
		}
	}

	public void showAppeal(Tile currentTile, Graphics newg)
	{
		if (currentTile.getAppeal() == 0)
		{
			newg.setColor(new Color(152, 152, 152));
		}
		else if (currentTile.getAppeal() == 1)
		{
			newg.setColor(new Color(112, 112, 112));

		}
		else if (currentTile.getAppeal() == 2)
		{
			newg.setColor(new Color(88, 88, 88));

		}
		else if (currentTile.getAppeal() == 3)
		{
			newg.setColor(new Color(64, 64, 64));

		}
		else if (currentTile.getAppeal() == 4)
		{
			newg.setColor(new Color(32, 32, 32));
		}
	}

	public int getGridWidth()
	{
		return rectWidth;
	}

	public int getGridHeight()
	{
		return rectHeight;
	}

	public void update(Observable o, Object arg)
	{
	}

}

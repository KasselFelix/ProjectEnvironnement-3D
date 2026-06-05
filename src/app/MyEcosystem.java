// ### WORLD OF CELLS ###
// created by nicolas.bredeche(at)upmc.fr
// date of creation: 2013-1-12

package app;

import graphics.Landscape;
import ui.SimulationConfig;
import worlds.WorldOfCells;

public class MyEcosystem {

	public static void main(String[] args) {

		WorldOfCells myWorld = new WorldOfCells();

		SimulationConfig config = new SimulationConfig();
		SimulationConfig.setInstance(config); // Module 1 — accès singleton pour LavaCA

		Landscape myLandscape = new Landscape(myWorld, config);
		Landscape.run(myLandscape);
	}

}

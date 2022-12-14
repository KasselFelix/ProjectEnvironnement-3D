// ### WORLD OF CELLS ### 
// created by nicolas.bredeche(at)upmc.fr
// date of creation: 2013-1-12

package applications.simpleworld;

import graphics.Landscape;

public class MyEcosystem {
    
	public static void main(String[] args) {
		
		WorldOfCells myWorld = new WorldOfCells();
		
		// parametres:
		// 1: le "monde" (ou sont definis vos automates cellulaires et agents
		// 2: (ca depend de la methode : generation aleatoire ou chargement d'image)
		// 3: l'amplitude de l'altitude (plus la valeur est elevee, plus haute sont les montagnes)
		// 4: la quantite d'eau
		Landscape myLandscape = new Landscape(myWorld, 128,128 , 0.7, 0.4);
		//Landscape myLandscape = new Landscape(myWorld, "landscape_default-128.png", 0.8, 0.4);		
		Landscape.run(myLandscape);
    }

}

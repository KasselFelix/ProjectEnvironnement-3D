package applications.simpleworld;

import javax.media.opengl.GL2;

import objects.UniqueDynamicObject;

import worlds.World;

public class Loup extends Agent {

	boolean _alive;

	double PreproD = 0.0009;//
	double Prepro = PreproD;

	int energieD = 1000;// 20
	int energie = energieD;

	int vision = 10;

	// block/s MAX:28
	double vcourse = 13.5;
	double vtrot = 8;
	double vpas = 3;
	double vitesse = vpas;

	int m = 0;// 1 si a manger ce tour
	int poursuite = 0;// 1 si a poursuit une proie ce tour
	int attaqueNuit = 0;
	int stepSpi = 0;
	int stepSpiF = 1;
	int earthSearch = 0;// 1 si est dans l'eau et recherche la terre ferme
	int waterSearch=0;// 1 si est sur terre et recherche de l'eau
	int imobil = 0;
	

	int lastX;
	int lastY;

	public Loup(int __x, int __y, World __world) {
		super(__x, __y, __world);
		_alive = true;

		_redValue = 1.f;
		_greenValue = 0.f;
		_blueValue = 0.f;
	
	}

	public void step() {
		// met a jour l'agent

		if ((world.getIteration() % world.getDureeJour() >= world.getTransitionJour() && world.getJour() == 0)
				|| (world.getIteration() % world.getDureeJour() >= world.getTransitionJour() 
				&& (world.getBefore() == 1 && world.getJour() == 0)))
					attaqueNuit = 1;

		if (world.getJour() == 1)attaqueNuit = 0;

		if (world.getIteration() % (int) ((1.0 / vitesse) * 28) == 0) {

			m = 0;
			poursuite = 0;
			imobil = 0;
			if (world.getCellHeight(x, y) >= 0) earthSearch = 0;
			if(world.getCellHeight(x, y)<0){this._fireState=0;waterSearch=0;}
			

			lastX=x;
			lastY=y;

			int xn = x;
			int yn = (y - 1 + this.world.getHeight()) % this.world.getHeight();
			int xe = (x + 1 + this.world.getWidth()) % this.world.getWidth();
			int ye = y;
			int xs = x;
			int ys = (y + 1 + this.world.getHeight()) % this.world.getHeight();
			int xo = (x - 1 + this.world.getWidth()) % this.world.getWidth();
			int yo = y;

			
			
			// ARBRRE COMPORTEMENT//
			
			// 0 : si en feu
			if(_fireState==1){
				double distEau=vision+1;//distance entre le predateur  et l'eau la plus proche a porter de vue
				int peo=-1;//proche eau orientation
				for(int r=1;r<=vision;r++){
					for(int i=(x-r+this.world.getWidth())%this.world.getWidth();i!=(x+r+1+this.world.getWidth())%this.world.getWidth();i=(i+1+this.world.getWidth())%this.world.getWidth()){
						for(int j=(y-r+this.world.getHeight())%this.world.getHeight();j!=(y+r+1+this.world.getHeight())%this.world.getHeight();j=(j+1+this.world.getHeight())%this.world.getHeight()){		

							if(_fireState==1){
								if( j==(y-r+this.world.getHeight())%this.world.getHeight())
									if(world.getCellHeight(i,j)>=0){
										if(distance(i,j)<distEau){
											peo=0;
											distEau=distance(i,j);
										}
									}
								if(i==(x+r+this.world.getWidth())%this.world.getWidth())
									if(world.getCellHeight(i,j)>=0){
										if(distance(i,j)<distEau){
											peo=1;
											distEau=distance(i,j);
										}
									}
								if(j==(y+r+this.world.getHeight())%this.world.getHeight())
									if(world.getCellHeight(i,j)>=0){
										if(distance(i,j)<distEau){
											peo=2;
											distEau=distance(i,j);
										}
									}
								if(i==(x-r+this.world.getWidth())%this.world.getWidth()){
									if(world.getCellHeight(i,j)>=0){
										if(distance(i,j)<distEau){
											peo=3;
											distEau=distance(i,j);
										}
									}
								}
								if(peo!=-1){
									this._orient=peo;
								}
								else if(waterSearch==0)this._orient=(_orient+2) %4;
								vitesse=vcourse;
							}
						}
					}
				}
			}else{

				double dista = vision + 1;// distance entre l'agent et la proie le plus proche a porter de vue
				int pao = 0;// orientation e direction de la proie la plus proche a porter de vue

				// 1 : Chasse si il fait nuit ou faible energie
				if (energie < energieD * 0.7 || attaqueNuit == 1) {
					for (int r = 1; r <= vision; r++) {
						for (int i = (x - r + this.world.getWidth())% this.world.getWidth(); i != (x + r + 1 + this.world.getWidth()) % this.world.getWidth(); i = (i + 1 + this.world.getWidth()) % this.world.getWidth()) {
							for (int j = (y - r + this.world.getHeight())% this.world.getHeight(); j != (y + r + 1 + this.world.getHeight()) % this.world.getHeight(); j = (j + 1 + this.world.getHeight()) % this.world.getHeight()) {
								// poursuite si proie autour
								for (UniqueDynamicObject a : this.world.moutons) {
									if (xn == a.x && yn == a.y) {
										this._orient = 0;
										poursuite = 1;
										vitesse = vcourse;
										// System.out.println("poursuite");
										break;
									}
									if (xe == a.x && ye == a.y) {
										this._orient = 1;
										poursuite = 1;
										vitesse = vcourse;
										// System.out.println("poursuite");
										break;
									}
									if (xs == a.x && ys == a.y) {
										this._orient = 2;
										poursuite = 1;
										vitesse = vcourse;
										// System.out.println("poursuite");
										break;
									}
									if (xo == a.x && yo == a.y) {
										this._orient = 3;
										poursuite = 1;
										vitesse = vcourse;
										// System.out.println("poursuite");
										break;
									}

									// sinon
									if (j == (y - r + this.world.getHeight())% this.world.getHeight())
										if (i == a.x && j == a.y) {
											this._orient = 0;
											poursuite = 1;
											if (distance(a.x, a.y) < dista) {
												pao = this._orient;
												dista = distance(a.x, a.y);
											}
											// System.out.println("hunt");
										}
									if (i == (x + r + this.world.getWidth())% this.world.getWidth())
										if (i == a.x && j == a.y) {
											this._orient = 1;
											poursuite = 1;
											if (distance(a.x, a.y) < dista) {
												pao = this._orient;
												dista = distance(a.x, a.y);
											}
											// System.out.println("hunt");
										}
									if (j == (y + r + this.world.getHeight())% this.world.getHeight())
										if (i == a.x && j == a.y) {
											this._orient = 2;
											poursuite = 1;
											if (distance(a.x, a.y) < dista) {
												pao = this._orient;
												dista = distance(a.x, a.y);
											}
											// System.out.println("hunt");
										}
									if (i == (x - r + this.world.getWidth())% this.world.getWidth()) {
										if (i == a.x && j == a.y) {
											this._orient = 3;
											poursuite = 1;
											if (distance(a.x, a.y) < dista) {
												pao = this._orient;
												dista = distance(a.x, a.y);
											}
											// System.out.println("hunt");
										}
									}
									if (poursuite == 1) {
										this._orient = pao;
										if (world.getCellHeight(x, y) >= 0)
											vitesse = vcourse;
										else
											vitesse = vcourse / 3;
									}
								}
							}
						}
					}

					// 1.2 : si aucune proie reperer
					if (poursuite == 0) {
						// 1.2.1 : si dans l'eau recherche terre
						if (world.getCellHeight(x, y) < 0) {
							earthSearch = 1;
							double disTerre = vision + 1;
							int pto = -1;
							for (int r = 1; r <= vision; r++) {
								for (int i = (x - r + this.world.getWidth())% this.world.getWidth(); i != (x + r + 1 + this.world.getWidth()) % this.world.getWidth(); i = (i + 1 + this.world.getWidth()) % this.world.getWidth()) {
									for (int j = (y - r + this.world.getHeight())% this.world.getHeight(); j != (y + r + 1 + this.world.getHeight())% this.world.getHeight(); j = (j + 1 + this.world.getHeight()) % this.world.getHeight()) {
										if (j == (y - r + this.world.getHeight())% this.world.getHeight())
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 0;
													disTerre = distance(i, j);
												}
											}
										if (i == (x + r + this.world.getWidth())% this.world.getWidth())
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 1;
													disTerre = distance(i, j);
												}
											}
										if (j == (y + r + this.world.getHeight())% this.world.getHeight())
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 2;
													disTerre = distance(i, j);
												}
											}
										if (i == (x - r + this.world.getWidth())% this.world.getWidth()) {
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 3;
													disTerre = distance(i, j);
												}
											}
										}
										if (pto != -1) {
											this._orient = pto;
										} else if (earthSearch == 0)
											this._orient = (_orient + 2) % 4;
									}
								}
							}
							vitesse = vcourse / 3;
						} else {// 1.2.2 : sinon deplacement en spirale
							if (stepSpi == stepSpiF) {
								this._orient = (_orient + 1) % 4;
								stepSpiF += vision / 2;
								stepSpi = 0;
							} else {
								stepSpi++;
							}
							vitesse = vtrot;
						}
					}

				} else {
					if (poursuite == 0) {
						// 2.1 : si dans l'eau recherche terre
						if (world.getCellHeight(x, y) < 0) {
							earthSearch = 1;
							double disTerre = vision + 1;
							int pto = -1;
							for (int r = 1; r <= vision; r++) {
								for (int i = (x - r + this.world.getWidth())% this.world.getWidth(); i != (x + r + 1 + this.world.getWidth()) % this.world.getWidth(); i = (i + 1 + this.world.getWidth()) % this.world.getWidth()) {
									for (int j = (y - r + this.world.getHeight())% this.world.getHeight(); j != (y + r + 1 + this.world.getHeight())% this.world.getHeight(); j = (j + 1 + this.world
											.getHeight()) % this.world.getHeight()) {
										if (j == (y - r + this.world.getHeight())% this.world.getHeight())
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 0;
													disTerre = distance(i, j);
												}
											}
										if (i == (x + r + this.world.getWidth())% this.world.getWidth())
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 1;
													disTerre = distance(i, j);
												}
											}
										if (j == (y + r + this.world.getHeight())% this.world.getHeight())
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 2;
													disTerre = distance(i, j);
												}
											}
										if (i == (x - r + this.world.getWidth())% this.world.getWidth()) {
											if (world.getCellHeight(i, j) >= 0) {
												if (distance(i, j) < disTerre) {
													pto = 3;
													disTerre = distance(i, j);
												}
											}
										}
										if (pto != -1) {
											this._orient = pto;
										} else if (earthSearch == 0)
											this._orient = (_orient + 2) % 4;
									}
								}
							}
							vitesse = vcourse / 3;
						} else {// 2.2 :sinon deplacement au hasard
							if (Math.random() < 0.2) {
								imobil = 1;
								if (Math.random() > 0.5)
									this._orient = (_orient + 1) % 4;
								else
									this._orient = (_orient - 1 + 4) % 4;
								vitesse = 1;
							} else {
								vitesse = vpas;
							}
						}
					}
				}
			}
			////////////////
			
			// met a jour: la position de l'agent (depend de l'orientation)
			if (energie > 2 && imobil == 0) {
				switch (_orient) {
				case 0: // nord
					if (world.getForestCAValue(xn, yn) == 0
							&& world.getForestCAValue(xn, yn) == 0
							&& (((world.getCellHeight(xn, yn)>=0 || waterSearch==1) ||poursuite==1) || earthSearch==1)){
						this.y = yn;
					} else {
						int i;
						int j;
						int rx;
						int ry;
						int cpt = 20;
						do {
							i = (int) (Math.random() * 3) - 1;
							j = (int) (Math.random() * 3) - 1;
							if (i == 0 && j == 0) {
								i = 0;
								j = -1;
							}
							if (i == 0 && j == 1 && poursuite == 1) {
								i = -1;
								j = 0;
							}
							rx = (x + i + this.world.getWidth()) % this.world.getWidth();
							ry = (y + j + this.world.getHeight()) % this.world.getHeight();
							cpt--;
						} while ((world.getForestCAValue(rx, ry) != 0
								|| world.getLaveCAValue(rx, ry) != 0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==0) && poursuite==0))) 
								&& cpt != 0);
						if (cpt != 0) {
							this.x = (x + i + this.world.getWidth())% this.world.getWidth();
							this.y = (y + j + this.world.getHeight())% this.world.getHeight();
						}
					}
					break;
				case 1: // est
					if (world.getForestCAValue(xe, ye) == 0
							&& world.getLaveCAValue(xe, ye) == 0
							&& (((world.getCellHeight(xn, yn)>=0 || waterSearch==1) ||poursuite==1) || earthSearch==1)){
						this.x = xe;
					} else {
						int i;
						int j;
						int rx;
						int ry;
						int cpt = 20;
						do {
							i = (int) (Math.random() * 3) - 1;
							j = (int) (Math.random() * 3) - 1;
							if (i == 0 && j == 0) {
								i = 1;
								j = 0;
							}
							if (i == -1 && j == 0 && poursuite == 1) {
								i = -1;
								j = 0;
							}
							rx = (x + i + this.world.getWidth())% this.world.getWidth();
							ry = (y + j + this.world.getHeight())% this.world.getHeight();
							cpt--;
						} while ((world.getForestCAValue(rx, ry) != 0
								|| world.getLaveCAValue(rx, ry) != 0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==0) && poursuite==0)))
								&& cpt != 0);
						if (cpt != 0) {
							this.x = (x + i + this.world.getWidth())% this.world.getWidth();
							this.y = (y + j + this.world.getHeight())% this.world.getHeight();
						}
					}
					break;
				case 2: // sud
					if (world.getForestCAValue(xs, ys) == 0
							&& world.getLaveCAValue(xs, ys) == 0
							&& (((world.getCellHeight(xn, yn)>=0 || waterSearch==1) ||poursuite==1) || earthSearch==1)){
						this.y = ys;
					} else {
						int i;
						int j;
						int rx;
						int ry;
						int cpt = 20;
						do {
							i = (int) (Math.random() * 3) - 1;
							j = (int) (Math.random() * 3) - 1;
							if (i == 0 && j == 0) {
								i = 0;
								j = 1;
							}
							if (i == 0 && j == -1 && poursuite == 1) {
								i = -1;
								j = 0;
							}
							rx = (x + i + this.world.getWidth())% this.world.getWidth();
							ry = (y + j + this.world.getHeight())% this.world.getHeight();
							cpt--;
						} while ((world.getForestCAValue(rx, ry) != 0
								|| world.getLaveCAValue(rx, ry) != 0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==0) && poursuite==0)))
								&& cpt != 0);
						if (cpt != 0) {
							this.x = (x + i + this.world.getWidth())% this.world.getWidth();
							this.y = (y + j + this.world.getHeight())% this.world.getHeight();
						}
					}
					break;
				case 3: // ouest
					if (world.getForestCAValue(xo, yo) == 0
							&& world.getLaveCAValue(xo, yo) == 0
							&& (((world.getCellHeight(xn, yn)>=0 || waterSearch==1) ||poursuite==1) || earthSearch==1)){
						this.x = xo;
					} else {
						int i;
						int j;
						int rx;
						int ry;
						int cpt = 20;
						do {
							i = (int) (Math.random() * 3) - 1;
							j = (int) (Math.random() * 3) - 1;
							if (i == 0 && j == 0) {
								i = -1;
								j = 0;
							}
							if (i == 1 && j == 0 && poursuite == 1) {
								i = -1;
								j = 0;
							}
							rx = (x + i + this.world.getWidth())% this.world.getWidth();
							ry = (y + j + this.world.getHeight())% this.world.getHeight();
							cpt--;
						} while ((world.getForestCAValue(rx, ry) != 0
								|| world.getLaveCAValue(rx, ry) != 0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==0) && poursuite==0)))
								&& cpt != 0);
						if (cpt != 0) {
							this.x = (x + i + this.world.getWidth())% this.world.getWidth();
							this.y = (y + j + this.world.getHeight())% this.world.getHeight();
						}
					}
				}
			}
			
			xn=x;
			yn=(y-1+this.world.getHeight())%this.world.getHeight();
			xe=(x+1+this.world.getWidth())%this.world.getWidth();
			ye=y;
			xs=x;
			ys=(y+1+this.world.getHeight())%this.world.getHeight();
			xo=(x-1+this.world.getWidth())%this.world.getWidth();
			yo=y;

			// mange
			if (energie < energieD * 0.9) {
				for (Mouton ag : this.world.moutons) {
					UniqueDynamicObject pag = (UniqueDynamicObject) ag;
					if (pag.x == x && pag.y == y) {
						ag._alive = false;
						energie = energieD;
						m = 1;
						vitesse = vpas;
						if (attaqueNuit == 1)attaqueNuit = 0;
						// System.out.println("devore");
						break;
					}
				}
			}


			// si rencontre feu
			if (world.getForestCAValue(xn, yn) == 2
					|| world.getForestCAValue(xe, ye) == 2
					|| world.getForestCAValue(xs, ys) == 2
					|| world.getForestCAValue(xo, yo) == 2) {
				_fireState= 1;
			}

			// si renconre lave
			if (world.getLaveCAValue(xn, yn) >0
					|| world.getLaveCAValue(xe, ye) >0
					|| world.getLaveCAValue(xs, ys) >0
					|| world.getLaveCAValue(xo, yo) >0) {
				vitesse = vcourse;
			}

			// mise a jour energie
			if (energie <= 0) {
				_alive = false;
			} else {
				if (world.getCellHeight(x, y) < 0)energie-=2;
				if( world.getCellHeight(lastX, lastY) > world.getCellHeight(x, y)){
					energie--;
				}
				energie--;
			}
			if (energie < 10 && vitesse >= vcourse) {
				vitesse = vcourse / 2;
			}
			if (energie < 5) {
				vitesse = vpas;
			}
			
			//vitesse reduite en hauteur
			if(world.getCellHeight(x,y)>0)vitesse=vitesse-((vitesse*0.75)*world.getCellHeight(x,y));
			
			
			// si dans la lave
			if (_world.getLaveCAValue(x, y) > 0) {
				_alive = false;
			}
			
			
			// reproduction
			if (Math.random() < Prepro) {
				Loup prea = new Loup(world.getWolfHome()% this.world.getWidth(),
						world.getWolfHome()/ this.world.getHeight(),
						this._world);
				this.world.uniqueDynamicObjects.add(prea);
				this.world.agents.add(prea);
				this.world.loups.add(prea);
				this.world.setNbloups(world.getNbloups() + 1);
			}
			
			
			// limtation reproduction
			if (this.world.getNbloups() < 10) {
				Prepro = PreproD * 2;
			} else if (this.world.getNbloups() > 20) {
				Prepro = 0;
			} else
				Prepro = PreproD;
			
		}
		if ( world.getIteration() % 20 == 0 )if(_fireState==1)energie-=energieD/10;
	}

	//calcul distance entre un point et l'agent dans un monde torique
	public double distance(int ib, int jb) {
		double tmp = Double.MAX_VALUE;
		for (int i = -1; i < 2; i++) {
			ib = ib + i * this.world.getWidth();
			for (int j = -1; j < 2; j++) {
				jb = jb + j * this.world.getHeight();
				tmp = Math.min(tmp,Math.abs(Math.sqrt((jb - y) * (jb - y) + (ib - x)* (ib - x))));
				jb = jb - j * this.world.getHeight();
			}
			ib = ib - i * this.world.getWidth();
		}
		return tmp;
	}

	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x,
			int offsetCA_y, float offset, float stepX, float stepY, float lenX,
			float lenY, float normalizeHeight) {

		// gl.glColor3f(0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()));

		int x2 = (x - (offsetCA_x % myWorld.getWidth()));
		if (x2 < 0)
			x2 += myWorld.getWidth();
		int y2 = (y - (offsetCA_y % myWorld.getHeight()));
		if (y2 < 0)
			y2 += myWorld.getHeight();

		float height = (float) myWorld.getCellHeight(x, y);
		float altitude = (float) (height * normalizeHeight);
		if (myWorld.getStoneCAValue(x2 + offsetCA_x, y2 + offsetCA_y) == 1)altitude += 3;
		if (altitude < 0)altitude = -1;

		gl.glColor3f(0.f, 0.f, 0.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude);

		gl.glColor3f(0.f, 0.f, 0.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude);

		gl.glColor3f(0.3f, 0.3f, 0.3f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude);

		gl.glColor3f(0.3f, 0.3f, 0.3f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude);

		gl.glColor3f(0.3f, 0.3f, 0.3f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY + lenY,altitude + 2.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 2.f);
		
		if (_fireState == 1) {
			_redValue = 1.f;
			_greenValue = 1.f;
			_blueValue = 0.f;
		}else if (m != 1) {
			if (energie < 3) {
				_redValue = 1.f;
				_greenValue = 200.f / 255.f;
				_blueValue = 205.f / 255.f;
			} else {
				_redValue = 1.f;
				_greenValue = 0.f;
				_blueValue = 0.f;
			}
		} else {
			_redValue = 1.f;
			_greenValue = 0.f;
			_blueValue = 1.f;
		}

		gl.glColor3f(_redValue, _greenValue, _blueValue);
		gl.glVertex3f(offset + x2 * stepX, offset + y2 * stepY + lenY,altitude + 5.f);
		gl.glVertex3f(offset + x2 * stepX - lenX, offset + y2 * stepY - lenY,altitude + 5.f);
		gl.glVertex3f(offset + x2 * stepX, offset + y2 * stepY + lenY,altitude + 5.f);
		gl.glVertex3f(offset + x2 * stepX + lenX, offset + y2 * stepY - lenY,altitude + 5.f);
	}

}

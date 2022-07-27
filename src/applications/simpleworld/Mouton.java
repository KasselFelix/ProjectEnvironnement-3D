package applications.simpleworld;


import javax.media.opengl.GL2;

import objects.UniqueDynamicObject;

import worlds.World;

public class Mouton extends Agent {

	boolean _alive;
	
	double PreproD=0.02;//0.06
	double Prepro=PreproD;
	
	double energieMAX=1000;//10
	double energie=energieMAX;//10
	int vision=10;
	
	// block/s MAX:28
	double vcourse=9;//14 test algo de fuite
	double vmarche=2;
	double vitesse=vmarche;
	
	int earthSearch=0;// 1 si est dans l'eau et recherche la terre ferme
	int waterSearch=0;// 1 si est sur terre et recherche de l'eau

	int fuite=0;
	int m=0;
	int imobil=0;
	int lastX;
	int lastY;

	public Mouton( int __x, int __y, World __World )
	{
		super(__x,__y,__World);
		_alive = true;

		_redValue = 0.f;
		_greenValue = 0.f;
		_blueValue = 1.f;
		
	}

	// met a jour l'agent
	public void step( )
	{

		if ( world.getIteration() % (int)((1.0/vitesse)*28) == 0 )
		{

			fuite=0;
			m=0;
			imobil=0;
			if(world.getCellHeight(x, y)>=0)earthSearch=0;
			if(world.getCellHeight(x, y)<0){this._fireState=0;waterSearch=0;}
			

			lastX=x;
			lastY=y;

			int xn=x;
			int yn=(y-1+this.world.getHeight())%this.world.getHeight();
			int xe=(x+1+this.world.getWidth())%this.world.getWidth();
			int ye=y;
			int xs=x;
			int ys=(y+1+this.world.getHeight())%this.world.getHeight();
			int xo=(x-1+this.world.getWidth())%this.world.getWidth();
			int yo=y;

			//INTERACTION INTERAGENTS
			for(Agent a : this.world.agents) {
				//si rencontre un agent en feu
				if( (xn == a.x && yn == a.y && a._fireState==1) ||
					(xe == a.x && ye == a.y && a._fireState==1) ||
					(xs == a.x && ys == a.y && a._fireState==1) ||
					(xo == a.x && yo == a.y && a._fireState==1) ){
						this._fireState=1;//prend feu 
				}
			}



			int an=-1;//0 si predateur au nord / -1 si aucun predateur au nord
			int ae=-1;//1 si predateur a est / -1 si aucun predateur a est
			int as=-1;//2 si predateur au sud / -1 si aucun predateur au sud
			int ao=-1;//3 si predateur a ouest / -1 si aucun predateur a ouest
			double dista=vision+1;;//distance entre l'agent et le predateur le plus proche a porter de vue
			int pao=0;//orientation opposer au predateur le plus proche a porter de vue
			double distEau=vision+1;//distance entre le predateur  et l'eau la plus proche a porter de vue
			int peo=-1;//proche eau orientation

			//champ de vision
			for(int r=1;r<=vision;r++){
				for(int i=(x-r+this.world.getWidth())%this.world.getWidth();i!=(x+r+1+this.world.getWidth())%this.world.getWidth();i=(i+1+this.world.getWidth())%this.world.getWidth()){
					for(int j=(y-r+this.world.getHeight())%this.world.getHeight();j!=(y+r+1+this.world.getHeight())%this.world.getHeight();j=(j+1+this.world.getHeight())%this.world.getHeight()){		
						//si en feu
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
						}else{
							
							//si predateur autour : fuite=1
							//si le Predateur et a 1 case
							for(UniqueDynamicObject a : this.world.loups) {
								if(r==1){// va dans la direction opposer a ce predateur
									if( xn == a.x && yn == a.y ){
										this._orient=2;
										fuite=1;
										an=0;
										vitesse=vcourse;
										//System.out.println("fuite");
										break;
									}
									if( xe == a.x && ye == a.y){
										this._orient=3;
										fuite=1;
										ae=1;
										vitesse=vcourse;
										//System.out.println("fuite");
										break;

									}
									if( xs == a.x && ys == a.y){
										this._orient=0;
										fuite=1;
										as=2;
										vitesse=vcourse;
										//System.out.println("fuite");
										break;
									}
									if(xo == a.x && yo == a.y ){
										this._orient=1;
										fuite=1;
										ao=3;
										vitesse=vcourse;
										//System.out.println("fuite");
										break;
									}
								}

								//sinon
								if( i==(x+r+this.world.getWidth())%this.world.getWidth()){//regarde a est
									if( i == a.x && j == a.y) {
										//predateur reperer a est
										this._orient=3;
										fuite=1;
										ae=1;
										if(distance(a.x,a.y)<dista){pao=this._orient;dista=distance(a.x,a.y);}
										//System.out.println("fuite");
									}
								}
								if (j==(y-r+this.world.getHeight())%this.world.getHeight()) {//regarde au nord
									if( i == a.x && j == a.y) {
										//predateur reperer au nord
										this._orient=2;
										fuite=1;
										an=0;
										if(distance(a.x,a.y)<dista){pao=this._orient;dista=distance(a.x,a.y);}
										//System.out.println("fuite");
									}
								}
								if(	i==(x-r+this.world.getWidth())%this.world.getWidth()){//regarde a ouest
									if( i == a.x && j == a.y) {
										//predateur reperer a ouest
										this._orient=1;
										fuite=1;
										ao=3;
										vitesse=vcourse;
										if(distance(a.x,a.y)<dista){pao=this._orient;dista=distance(a.x,a.y);}
										//System.out.println("fuite");
									}
								}
								if(j==(y+r+this.world.getHeight())%this.world.getHeight() ){//regarde au sud
									if( i == a.x && j == a.y) {
										//predateur reperer au sud
										this._orient=0;
										fuite=1;
										as=2;
										if(distance(a.x,a.y)<dista){pao=this._orient;dista=distance(a.x,a.y);}
										//System.out.println("fuite");
									}
								}
								if(fuite==1){
									this._orient=pao;// va dans l'orientation opposer au predateur le plus proche a porter de vue
									if(world.getCellHeight(x, y)>=0)vitesse=vcourse;
									else vitesse=vcourse/3;
								}
							}
						}
					}
				}
			}

			// si n'est pas en feu
			if( _fireState==0){
				//si predateur reperer
				if(fuite==1 ){
					if(this._orient==ae && (an==-1 || as==-1)){//si predateur a l'est et voie libre au nord ou au sud  
						if (Math.random() > 0.5 ){ //deplacement au hasard 
							if(an==-1){this._orient = 0;}
							else{this._orient = 2;}
						}else{
							if(as==-1){this._orient = 2;}
							else{this._orient = 0;}
						}
					}
					else if(this._orient==an && (ao==-1 || ae==-1)){//si predateur au nord et voie libre a ouest ou a est
						if (Math.random() > 0.5 ){ //deplacement au hasard
							if(ao==-1){this._orient = 3;}
							else{this._orient = 1;}
						}else{
							if(ae==-1){this._orient = 1;}
							else{this._orient = 3;}
						}
					}
					else if(this._orient==as && (ae==-1 || ao==-1)){//si predateur a sud et voie libre a est ou a ouest
						if (Math.random() > 0.5 ){ //deplacement au hasard
							if(ao==-1){this._orient = 3;}
							else{this._orient = 1;}
						}else{
							if(ae==-1){this._orient = 1;}
							else{this._orient = 3;}
						}
					}
					else{
						if(this._orient==ao && (an==-1 || as==-1)){//si predateur a l'ouest et voie libre au nord ou au sud  
							if (Math.random() > 0.5 ){ //deplacement au hasard
								if(an==-1) this._orient = 0;
								else this._orient = 2;
							}else{
								if(as==-1)this._orient = 2;
								else this._orient = 0;
							}
						}
					}
				}

				//si aucun predateur
				if(fuite==0){
					//si dans l'eau recherche terre
					if(world.getCellHeight(x, y)<0){
						earthSearch=1;
						double disTerre=vision+1;
						int pto=-1;
						for(int r=1;r<=vision;r++){
							for(int i=(x-r+this.world.getWidth())%this.world.getWidth();i!=(x+r+1+this.world.getWidth())%this.world.getWidth();i=(i+1+this.world.getWidth())%this.world.getWidth()){
								for(int j=(y-r+this.world.getHeight())%this.world.getHeight();j!=(y+r+1+this.world.getHeight())%this.world.getHeight();j=(j+1+this.world.getHeight())%this.world.getHeight()){		
									if( j==(y-r+this.world.getHeight())%this.world.getHeight())
										if(world.getCellHeight(i,j)>=0){
											if(distance(i,j)<disTerre){
												pto=0;
												disTerre=distance(i,j);
											}
										}
									if(i==(x+r+this.world.getWidth())%this.world.getWidth())
										if(world.getCellHeight(i,j)>=0){
											if(distance(i,j)<disTerre){
												pto=1;
												disTerre=distance(i,j);
											}
										}
									if(j==(y+r+this.world.getHeight())%this.world.getHeight())
										if(world.getCellHeight(i,j)>=0){
											if(distance(i,j)<disTerre){
												pto=2;
												disTerre=distance(i,j);
											}
										}
									if(i==(x-r+this.world.getWidth())%this.world.getWidth()){
										if(world.getCellHeight(i,j)>=0){
											if(distance(i,j)<disTerre){
												pto=3;
												disTerre=distance(i,j);
											}
										}
									}
									if(pto!=-1){
										this._orient=pto;
									}
									else if(earthSearch==0)this._orient=(_orient+2) %4;
								}
							}
						}
						vitesse=vcourse/3;
					}
					else{//deplacement au hasard
						
						if( Math.random() < 0.2){
							if( Math.random() > 0.5 ) 
								this._orient = (_orient+1) %4;
							else
								this._orient = (_orient-1+4) %4;
						}
						
						double dice = Math.random();
						if ( dice < 0.25 )
							this.x = ( this.x + 1 ) % this.world.getWidth() ;
						else
							if ( dice < 0.5 )
								this.x = ( this.x - 1 +  this.world.getWidth() ) % this.world.getWidth() ;
							else
								if ( dice < 0.75 )
									this.y = ( this.y + 1 ) % this.world.getHeight() ;
								else
									this.y = ( this.y - 1 +  this.world.getHeight() ) % this.world.getHeight() ;
						vitesse=vmarche;
					}
				}
			}

			//Broute
			if(energie<(energieMAX*0.75) &&fuite==0) {
				if(world.getHerbeCAValue( x, y)==1){
					world.setHerbeCAValue( x, y, 0);
					energie+=energieMAX/100;
					m=1;
					//System.out.println("broute");
				}
			}


			// met a jour: la position de l'agent (depend de l'orientation)
			if(energie>2 && imobil==0) {
				switch (_orient) {
				case 0: // nord	
					if(world.getForestCAValue(xn, yn)==0 
							&& world.getLaveCAValue(xn, yn)==0 
							&& (((world.getCellHeight(xn, yn)>=0 || waterSearch==1) ||fuite==1) || earthSearch==1)){
						this.y = yn;
					}else{
						int i;
						int j;
						int rx;
						int ry;
						int cpt=20;
						do{
							i=(int)(Math.random()*3)-1;
							j=(int)(Math.random()*3)-1;
							if(i==0 && j==0){i=0;j=-1;}
							if(i==0 && j==1 && fuite==1){i=-1;j=0;}
							rx=(x + i + this.world.getWidth()) % this.world.getWidth();
							ry=(y + j + this.world.getHeight()) % this.world.getHeight();
							cpt--;
						}while( ( world.getForestCAValue(rx,ry)!=0 
								|| world.getLaveCAValue(rx,ry)!=0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==0) &&fuite==0))) 
								&& cpt !=0);
						if(cpt!=0){
							this.x=(x + i + this.world.getWidth()) % this.world.getWidth();
							this.y=(y + j + this.world.getHeight()) % this.world.getHeight();
						}
					}
					break;
				case 1: // est
					if(world.getForestCAValue(xe, ye)==0 
							&& world.getLaveCAValue(xe, ye)==0 
							&& (((world.getCellHeight(xe, ye)>=0 || waterSearch==1)  ||fuite==1)|| earthSearch==1)){
						this.x = xe;
					}else{
						int i;
						int j;
						int rx;
						int ry;
						int cpt=20;
						do{
							i=(int)(Math.random()*3)-1;
							j=(int)(Math.random()*3)-1;
							if(i==0 && j==0){i=1;j=0;}
							if(i==-1 && j==0 && fuite==1){i=-1;j=0;}
							rx=(x + i + this.world.getWidth()) % this.world.getWidth();
							ry=(y + j + this.world.getHeight()) % this.world.getHeight();
							cpt--;
						}while( ( world.getForestCAValue(rx,ry)!=0 
								|| world.getLaveCAValue(rx,ry)!=0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==1) && fuite==0))) 
								&& cpt !=0 );
						if(cpt!=0){
							this.x=(x + i + this.world.getWidth()) % this.world.getWidth();
							this.y=(y + j + this.world.getHeight()) % this.world.getHeight();
						}
					}	
					break;
				case 2: // sud
					if(world.getForestCAValue(xs,ys)==0 
							&& world.getLaveCAValue(xs,ys)==0 
							&& (((world.getCellHeight(xs, ys)>=0 || waterSearch==1) || fuite==1) || earthSearch==1)){
						this.y = ys;
					}else{
						int i;
						int j;
						int rx;
						int ry;
						int cpt=20;
						do{
							i=(int)(Math.random()*3)-1;
							j=(int)(Math.random()*3)-1;
							if(i==0 && j==0){i=0;j=1;}
							if(i==0 && j==-1 && fuite==1){i=-1;j=0;}
							rx=(x + i + this.world.getWidth()) % this.world.getWidth();
							ry=(y + j + this.world.getHeight()) % this.world.getHeight();
							cpt--;
						}while( ( world.getForestCAValue(rx,ry)!=0 
								|| world.getLaveCAValue(rx,ry)!=0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==1) &&fuite==0))) 
								&& cpt !=0);
						if(cpt!=0){
							this.x=(x + i + this.world.getWidth()) % this.world.getWidth();
							this.y=(y + j + this.world.getHeight()) % this.world.getHeight();
						}
					}
					break;
				case 3: // ouest
					if(world.getForestCAValue(xo,yo)==0 
							&& world.getLaveCAValue(xo,yo)==0 
							&& (((world.getCellHeight(xo, yo)>=0 || waterSearch==1)||fuite==1) || earthSearch==1)){
						this.x = xo;
					}else{
						int i;
						int j;
						int rx;
						int ry;
						int cpt=20;
						do{
							i=(int)(Math.random()*3)-1;
							j=(int)(Math.random()*3)-1;
							if(i==0 && j==0){i=-1;j=0;}
							if(i==1 && j==0 && fuite==1){i=-1;j=0;}
							rx=(x + i + this.world.getWidth()) % this.world.getWidth();
							ry=(y + j + this.world.getHeight()) % this.world.getHeight();
							cpt--;
						}while(( world.getForestCAValue(rx,ry)!=0 
								|| world.getLaveCAValue(rx,ry)!=0 
								|| ( earthSearch==0 && ((world.getCellHeight(rx, ry)<0 && waterSearch==1) &&fuite==0))) 
								&& cpt !=0);
						if(cpt!=0){
							this.x=(x + i + this.world.getWidth()) % this.world.getWidth();
							this.y=(y + j + this.world.getHeight()) % this.world.getHeight();
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

			// si rencontre feu
			if(world.getForestCAValue(xn,yn)==2
					|| world.getForestCAValue(xe,ye)==2
					|| world.getForestCAValue(xs,ys)==2 
					|| world.getForestCAValue(xo,yo)==2){
				_fireState=1;
			}

			//si renconre lave
			if( world.getLaveCAValue(xn,yn)>0
					|| world.getLaveCAValue(xe,ye)>0
					|| world.getLaveCAValue(xs,ys)>0 
					|| world.getLaveCAValue(xo,yo)>0){
				vitesse=vcourse;
			}


			//mise a jour energie
			if(energie<=0){
				_alive = false;
			}else{
				if(world.getCellHeight(x, y)<0)energie-=2;
				if( world.getCellHeight(lastX, lastY) > world.getCellHeight(x, y)){
					energie--;
				}
				energie--;
			}
			if(energie<10 && vitesse>=vcourse){
				vitesse=vcourse/2;
			}
			if(energie<3){
				vitesse=vmarche;
			}
			
			//vitesse reduite en hauteur
			if(world.getCellHeight(x,y)>0)vitesse=vitesse-((vitesse*0.75)*world.getCellHeight(x,y));

			//si dans la lave
			if(_world.getLaveCAValue(x,y)>0) {
				_alive=false;
			}

			//reproduction
			if(Math.random()<Prepro) {
				Mouton prea=new Mouton(
						world.getBergerie()%this.world.getWidth(),
						world.getBergerie()/this.world.getHeight(),
						this._world);
				this.world.uniqueDynamicObjects.add(prea);
				this.world.agents.add(prea);
				this.world.moutons.add(prea);
				this.world.setNbmoutons(world.getNbmoutons()+1);
			}

			// limitation reproduction
			if(_world.getNbmoutons()<10){Prepro=PreproD*4;}
			else if(_world.getNbmoutons()> 20){Prepro=0;}
			else Prepro = PreproD;
		}
		if ( world.getIteration() % 20 == 0 )if(_fireState==1)energie-=energieMAX/10;
	}


	//calcul distance entre un point et l'agent dans un monde torique
	public double distance( int ib,int jb){
		double tmp=Double.MAX_VALUE;
		for(int i=-1;i<2;i++){
			ib=ib+i*this.world.getWidth();
			for(int j=-1;j<2;j++){
				jb=jb+j*this.world.getHeight();
				tmp=Math.min(tmp,Math.abs(Math.sqrt((jb-y)*(jb-y)+(ib-x)*(ib-x))));
				jb=jb-j*this.world.getHeight();
			}
			ib=ib-i*this.world.getWidth();
		}
		return tmp;
	}

	public void displayUniqueObject(World myWorld, GL2 gl, int offsetCA_x, int offsetCA_y, float offset, float stepX, float stepY, float lenX, float lenY, float normalizeHeight)
	{

		//gl.glColor3f(0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()),0.f+(float)(0.5*Math.random()));

		int x2 = (x-(offsetCA_x%myWorld.getWidth()));
		if ( x2 < 0) x2+=myWorld.getWidth();
		int y2 = (y-(offsetCA_y%myWorld.getHeight()));
		if ( y2 < 0) y2+=myWorld.getHeight();

		float height = (float)myWorld.getCellHeight(x, y);
		float altitude = (float)(height * normalizeHeight);
		if (myWorld.getStoneCAValue(x2+offsetCA_x, y2+offsetCA_y )==1) altitude+=3;
		if(altitude<0)altitude=-1;

		gl.glColor3f(1.f,1.f,1.f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude);

		gl.glColor3f(1.f,1.f,1.f);
		gl.glVertex3f( offset+x2*stepX+lenX+1, offset+y2*stepY+lenY, altitude);
		gl.glVertex3f( offset+x2*stepX+lenX+1, offset+y2*stepY+lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX-lenX-1, offset+y2*stepY+lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX-lenX-1, offset+y2*stepY+lenY, altitude);

		gl.glColor3f(0.8f,0.8f,0.8f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude);

		gl.glColor3f(0.8f,0.8f,0.8f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude);

		gl.glColor3f(0.5f,0.5f,0.5f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY+lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY+lenY, altitude + 2.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 2.f);
		
		if (_fireState== 1) {
			_redValue = 1.f;
			_greenValue = 1.f;
			_blueValue = 0.f;
		} else {
			_redValue = 0.f;
			_greenValue = 0.f;
			_blueValue = 1.f;
		}


		gl.glColor3f(_redValue,_greenValue,_blueValue);
		gl.glVertex3f( offset+x2*stepX, offset+y2*stepY+lenY, altitude + 4.f);
		gl.glVertex3f( offset+x2*stepX-lenX, offset+y2*stepY-lenY, altitude + 4.f);
		gl.glVertex3f( offset+x2*stepX, offset+y2*stepY+lenY, altitude + 4.f);
		gl.glVertex3f( offset+x2*stepX+lenX, offset+y2*stepY-lenY, altitude + 4.f);
	}
}

// WADDecompiler class

// Handles the actual decompilation.

import java.util.Date;

public class WADDecompiler {

	// INITIAL DATA DECLARATION AND DEFINITION OF CONSTANTS
	
	public static final int A = 0;
	public static final int B = 1;
	public static final int C = 2;
	
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;
	
	// These are lowercase so as not to conflict with A B and C
	// Light entity attributes; red, green, blue, strength (can't use i for intensity :P)
	public static final int r = 0;
	public static final int g = 1;
	public static final int b = 2;
	public static final int s = 3;
	
	private int jobnum;
	
	private Entities mapFile; // Most MAP file formats (including GearCraft) are simply a bunch of nested entities
	private int numBrshs;
	
	private DoomMap doomMap;
	
	// CONSTRUCTORS
	
	// This constructor sets up everything to convert a Doom map into brushes compatible with modern map editors.
	// I don't know if this is decompiling, per se. I don't know if Doom maps were ever compiled or if they just had nodes built.
	public WADDecompiler(DoomMap doomMap, int jobnum) {
		this.doomMap=doomMap;
		this.jobnum=jobnum;
	}
	
	// METHODS
	
	// +decompile()
	// Attempts to convert a map in a Doom WAD into a usable .MAP file. This has many
	// challenges, not the least of which is the fact that the Doom engine didn't use
	// brushes (at least, not in any sane way).
	public void decompile() throws java.io.IOException {
		Date begin=new Date();
		Window.println(doomMap.getMapName(),Window.VERBOSITY_ALWAYS);
		
		mapFile=new Entities();
		Entity world = new Entity("worldspawn");
		world.setAttribute("mapversion", "510");
		mapFile.add(world);
		
		String[] lowerWallTextures=new String[doomMap.getSidedefs().getNumElements()];
		String[] midWallTextures=new String[doomMap.getSidedefs().getNumElements()];
		String[] higherWallTextures=new String[doomMap.getSidedefs().getNumElements()];
		
		short[] sectorTag=new short[doomMap.getSectors().getNumElements()];
		
		// Since Doom relied on sectors to define a cieling and floor height, and nothing else,
		// need to find the minimum and maximum used Z values. This is because the Doom engine
		// is only a pseudo-3D engine. For all it cares, the cieling and floor extend to their
		// respective infinities. For a GC/Hammer map, however, this cannot be the case.
		int ZMin=32767;  // Even though the values in the map will never exceed these, use ints here to avoid
		int ZMax=-32768; // overflows, in case the map DOES go within 32 units of these values.
		for(int i=0;i<doomMap.getSectors().getNumElements();i++) {
			DSector currentSector=doomMap.getSectors().getSector(i);
			sectorTag[i]=currentSector.getTag();
			if(currentSector.getFloorHeight()<ZMin+32) {
				ZMin=currentSector.getFloorHeight()-32; // Can't use the actual value, because that IS the floor
			} else {
				if(currentSector.getCielingHeight()>ZMax-32) {
					ZMax=currentSector.getCielingHeight()+32; // or the cieling. Subtract or add a sane value to it.
				}
			}
		}
		
		// I need to analyze the binary tree and get more information, particularly the
		// parent nodes of each subsector and node, as well as whether it's the right or
		// left child of that node. These are extremely important, as the parent defines
		// boundaries for the children, as well as inheriting further boundaries from its
		// parents. These boundaries are invaluable for forming brushes.
		int[] nodeparents = new int[doomMap.getNodes().getNumElements()];
		boolean[] nodeIsLeft = new boolean[doomMap.getNodes().getNumElements()];
		
		for(int i=0;i<doomMap.getNodes().getNumElements();i++) {
			nodeparents[i]=-1; // There should only be one node left with -1 as a parent. This SHOULD be the root.
			for(int j=0;j<doomMap.getNodes().getNumElements();j++) {
				if(doomMap.getNodes().getNode(j).getChild1() == i) {
					nodeparents[i]=j;
					break;
				} else {
					if(doomMap.getNodes().getNode(j).getChild2() == i) {
						nodeparents[i]=j;
						nodeIsLeft[i]=true;
						break;
					}
				}
			}
		}
		
		// Keep a list of what subsectors belong to which sector
		int[] subsectorSectors = new int[doomMap.getSubSectors().getNumElements()];
		// Keep a list of what sidedefs belong to what subsector as well
		int[][] subsectorSidedefs = new int[doomMap.getSubSectors().getNumElements()][];
		
		// Figure out what sector each subsector belongs to, and what node is its parent.
		// Depending on sector "tags" this will help greatly in creation of brushbased entities,
		// and also helps in finding subsector floor and cieling heights.
		int[] ssparents = new int[doomMap.getSubSectors().getNumElements()];
		boolean[] ssIsLeft = new boolean[doomMap.getSubSectors().getNumElements()];
		for(int i=0;i<doomMap.getSubSectors().getNumElements();i++) {
			Window.println("Populating texture lists for subsector "+i,Window.VERBOSITY_BRUSHCREATION);
			// First, find the subsector's parent and whether it is the left or right child.
			ssparents[i]=-1; // No subsector should have a -1 in here
			for(int j=0;j<doomMap.getNodes().getNumElements();j++) {
				// When a node references a subsector, it is not referenced by negative
				// index, as future BSP versions do. The bits 0-14 ARE the index, and
				// bit 15 (which is the sign bit in two's compliment math) determines
				// whether or not it is a node or subsector. Therefore, we need to add
				// 2^15 to the number to produce the actual index.
				if(doomMap.getNodes().getNode(j).getChild1()+32768 == i) {
					ssparents[i]=j;
					break;
				} else {
					if(doomMap.getNodes().getNode(j).getChild2()+32768 == i) {
						ssparents[i]=j;
						ssIsLeft[i]=true;
						break;
					}
				}
			}
			
			// Second, figure out what sector a subsector belongs to, and the type of sector it is.
			subsectorSectors[i]=-1;
			DSubSector currentsubsector=doomMap.getSubSectors().getSubSector(i);
			subsectorSidedefs[i]=new int[currentsubsector.getNumSegs()];
			for(int j=0;j<currentsubsector.getNumSegs();j++) { // For each segment the subsector references
				DSegment currentsegment=doomMap.getSegments().getSegment(currentsubsector.getFirstSeg()+j);
				DLinedef currentlinedef=doomMap.getLinedefs().getElement(currentsegment.getLinedef());
				int currentsidedefIndex;
				int othersideIndex;
				if(currentsegment.getDirection()==0) {
					currentsidedefIndex=currentlinedef.getRight();
					othersideIndex=currentlinedef.getLeft();
				} else {
					currentsidedefIndex=currentlinedef.getLeft();
					othersideIndex=currentlinedef.getRight();
				}
				subsectorSidedefs[i][j]=currentsidedefIndex;
				DSidedef currentSidedef=doomMap.getSidedefs().getSide(currentsidedefIndex);
				if(currentlinedef.isOneSided()) {
					// A one-sided linedef should always be like this
					midWallTextures[currentsidedefIndex]=doomMap.getWadName()+"/"+currentSidedef.getMidTexture();
					higherWallTextures[currentsidedefIndex]="special/nodraw";
					lowerWallTextures[currentsidedefIndex]="special/nodraw";
				} else {
					// I don't really get why I need to apply these textures to the other side. But if it works I won't argue...
					if(!currentSidedef.getMidTexture().equals("-")) {
						midWallTextures[othersideIndex]=doomMap.getWadName()+"/"+currentSidedef.getMidTexture();
					} else {
						midWallTextures[othersideIndex]="special/nodraw";
					}
					if(!currentSidedef.getHighTexture().equals("-")) {
						higherWallTextures[othersideIndex]=doomMap.getWadName()+"/"+currentSidedef.getHighTexture();
					} else {
						higherWallTextures[othersideIndex]="special/nodraw";
					}
					if(!currentSidedef.getLowTexture().equals("-")) {
						lowerWallTextures[othersideIndex]=doomMap.getWadName()+"/"+currentSidedef.getLowTexture();
					} else {
						lowerWallTextures[othersideIndex]="special/nodraw";
					}
				}
				// Sometimes a subsector seems to belong to more than one sector. I don't know why.
				if(subsectorSectors[i]!=-1 && currentSidedef.getSector()!=subsectorSectors[i]) {
					Window.println("WARNING: Subsector "+i+" has sides defining different sectors!",Window.VERBOSITY_WARNINGS);
					Window.println("This is probably nothing to worry about, but something might be wrong (floor/cieling height)",Window.VERBOSITY_WARNINGS);
				} else {
					subsectorSectors[i]=currentSidedef.getSector();
				}
			}
		}
		boolean[] linedefFlagsDealtWith=new boolean[doomMap.getLinedefs().length()];
		boolean[] linedefSpecialsDealtWith=new boolean[doomMap.getLinedefs().length()];
		
		MAPBrush[][] sectorFloorBrushes=new MAPBrush[doomMap.getSectors().getNumElements()][0];
		MAPBrush[][] sectorCielingBrushes=new MAPBrush[doomMap.getSectors().getNumElements()][0];
		
		for(int i=0;i<doomMap.getSubSectors().getNumElements();i++) {
			Window.println("Creating brushes for subsector "+i,Window.VERBOSITY_BRUSHCREATION);
			
			DSubSector currentsubsector=doomMap.getSubSectors().getSubSector(i);
			
			// Third, create a few brushes out of the geometry.
			MAPBrush cielingBrush=new MAPBrush(numBrshs++, 0, false);
			MAPBrush floorBrush=new MAPBrush(numBrshs++, 0, false);
			MAPBrush midBrush=new MAPBrush(numBrshs++, 0, false);
			DSector currentSector=doomMap.getSectors().getSector(subsectorSectors[i]);
			
			Vector3D[] roofPlane=new Vector3D[3];
			double[] roofTexS=new double[3];
			double[] roofTexT=new double[3];
			roofPlane[0]=new Vector3D(0, Window.getPlanePointCoef(), ZMax);
			roofPlane[1]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), ZMax);
			roofPlane[2]=new Vector3D(Window.getPlanePointCoef(), 0, ZMax);
			roofTexS[0]=1;
			roofTexT[1]=-1;
			
			Vector3D[] cileingPlane=new Vector3D[3];
			double[] cileingTexS=new double[3];
			double[] cileingTexT=new double[3];
			cileingPlane[0]=new Vector3D(0, 0, currentSector.getCielingHeight());
			cileingPlane[1]=new Vector3D(Window.getPlanePointCoef(), 0, currentSector.getCielingHeight());
			cileingPlane[2]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), currentSector.getCielingHeight());
			cileingTexS[0]=1;
			cileingTexT[1]=-1;
			
			Vector3D[] floorPlane=new Vector3D[3];
			double[] floorTexS=new double[3];
			double[] floorTexT=new double[3];
			floorPlane[0]=new Vector3D(0, Window.getPlanePointCoef(), currentSector.getFloorHeight());
			floorPlane[1]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), currentSector.getFloorHeight());
			floorPlane[2]=new Vector3D(Window.getPlanePointCoef(), 0, currentSector.getFloorHeight());
			floorTexS[0]=1;
			floorTexT[1]=-1;

			Vector3D[] foundationPlane=new Vector3D[3];
			double[] foundationTexS=new double[3];
			double[] foundationTexT=new double[3];
			foundationPlane[0]=new Vector3D(0, 0, ZMin);
			foundationPlane[1]=new Vector3D(Window.getPlanePointCoef(), 0, ZMin);
			foundationPlane[2]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), ZMin);
			foundationTexS[0]=1;
			foundationTexT[1]=-1;

			Vector3D[] topPlane=new Vector3D[3];
			double[] topTexS=new double[3];
			double[] topTexT=new double[3];
			topPlane[0]=new Vector3D(0, Window.getPlanePointCoef(), currentSector.getCielingHeight());
			topPlane[1]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), currentSector.getCielingHeight());
			topPlane[2]=new Vector3D(Window.getPlanePointCoef(), 0, currentSector.getCielingHeight());
			topTexS[0]=1;
			topTexT[1]=-1;
	
			Vector3D[] bottomPlane=new Vector3D[3];
			double[] bottomTexS=new double[3];
			double[] bottomTexT=new double[3];
			bottomPlane[0]=new Vector3D(0, 0, currentSector.getFloorHeight());
			bottomPlane[1]=new Vector3D(Window.getPlanePointCoef(), 0, currentSector.getFloorHeight());
			bottomPlane[2]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), currentSector.getFloorHeight());
			bottomTexS[0]=1;
			bottomTexT[1]=-1;

			int nextNode=ssparents[i];
			boolean leftSide=ssIsLeft[i];

			for(int j=0;j<currentsubsector.getNumSegs();j++) { // Iterate through the sidedefs defined by segments of this subsector
				DSegment currentseg=doomMap.getSegments().getSegment(currentsubsector.getFirstSeg()+j);
				Vector3D start=doomMap.getVertices().getElement(currentseg.getStartVertex()).getVertex();
				Vector3D end=doomMap.getVertices().getElement(currentseg.getEndVertex()).getVertex();
				DLinedef currentLinedef=doomMap.getLinedefs().getElement((int)currentseg.getLinedef());
				DSidedef currentSidedef=doomMap.getSidedefs().getSide(subsectorSidedefs[i][j]);
				
				Vector3D[] plane=new Vector3D[3];
				double[] texS=new double[3];
				double[] texT=new double[3];
				plane[0]=new Vector3D(start.getX(), start.getY(), ZMin);
				plane[1]=new Vector3D(end.getX(), end.getY(), ZMin);
				plane[2]=new Vector3D(end.getX(), end.getY(), ZMax);
				
				double sideLength=Math.sqrt(Math.pow(start.getX()-end.getX(), 2) + Math.pow(start.getY()-end.getY(),2));
				
				texS[0]=(start.getX()-end.getX())/sideLength;
				texS[1]=(start.getY()-end.getY())/sideLength;
				texS[2]=0;
				texT[0]=0;
				texT[1]=0;
				texT[2]=-1;
				MAPBrushSide low=new MAPBrushSide(plane, lowerWallTextures[subsectorSidedefs[i][j]], texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
				MAPBrushSide high=new MAPBrushSide(plane, higherWallTextures[subsectorSidedefs[i][j]], texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
				MAPBrushSide mid;
				
				if(currentLinedef.isOneSided()) {
					MAPBrush outsideBrush=null;
					if(currentSector.getCielingHeight()<=currentSector.getFloorHeight()) {
						outsideBrush = GenericMethods.createFaceBrush(midWallTextures[subsectorSidedefs[i][j]], "special/nodraw", plane[0], plane[2], currentSidedef.getOffsetX(), currentSidedef.getOffsetY());
					} else {
						outsideBrush = GenericMethods.createFaceBrush(midWallTextures[subsectorSidedefs[i][j]], "special/nodraw", new Vector3D(plane[0].getX(), plane[0].getY(), currentSector.getFloorHeight()), new Vector3D(plane[2].getX(), plane[2].getY(), currentSector.getCielingHeight()), currentSidedef.getOffsetX(), currentSidedef.getOffsetY());
					}
					world.addBrush(outsideBrush);
					mid=new MAPBrushSide(plane, "special/nodraw", texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
				} else {
					mid=new MAPBrushSide(plane, midWallTextures[subsectorSidedefs[i][j]], texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
					if(!linedefFlagsDealtWith[currentseg.getLinedef()]) {
						linedefFlagsDealtWith[currentseg.getLinedef()]=true;
						if(!((currentLinedef.getFlags()[0] & ((byte)1 << 0)) == 0)) { // Flag 0x0001 indicates "solid" but doesn't block bullets. It is assumed for all one-sided.
							MAPBrush solidBrush = GenericMethods.createFaceBrush("special/clip", "special/clip", plane[0], plane[2],0,0);
							world.addBrush(solidBrush);
						} else {
							if(!((currentLinedef.getFlags()[0] & ((byte)1 << 1)) == 0)) { // Flag 0x0002 indicates "monster clip".
								MAPBrush solidBrush = GenericMethods.createFaceBrush("special/enemyclip", "special/enemyclip", plane[0], plane[2],0,0);
								world.addBrush(solidBrush);
							}
						}
					}
					int othersideindex=-1;
					if(currentLinedef.getRight()==subsectorSidedefs[i][j]) {
						othersideindex=currentLinedef.getLeft();
					}
					if(currentLinedef.getLeft()==subsectorSidedefs[i][j]) {
						othersideindex=currentLinedef.getRight();
					}
					DSidedef otherside=doomMap.getSidedefs().getSide(othersideindex);
					if(currentLinedef.getAction()!=0 && !linedefSpecialsDealtWith[currentseg.getLinedef()]) {
						linedefSpecialsDealtWith[currentseg.getLinedef()]=true;
						if(doomMap.getVersion()==DoomMap.TYPE_HEXEN) {
							if(currentLinedef.getAction()==70) { // Teleport
								Entity teleporter=new Entity("trigger_teleport");
								MAPBrush teleBrush = GenericMethods.createFaceBrush("special/trigger", "special/trigger", plane[0], plane[2],0,0);
								teleporter.addBrush(teleBrush);
								if(currentLinedef.getArguments()[0]!=0) {
									teleporter.setAttribute("target", "teledest"+currentLinedef.getArguments()[0]);
								} else {
									teleporter.setAttribute("target", "sector"+currentLinedef.getTag()+"teledest");
								}
								mapFile.add(teleporter);
							} else {
								if(currentLinedef.getAction()==181) { // PLANE_ALIGN
									if(!leftSide) {
										DSidedef getsector=doomMap.getSidedefs().getSide(currentLinedef.getLeft());
										DSector copyheight=doomMap.getSectors().getSector(getsector.getSector());
										short newHeight=copyheight.getFloorHeight();
										//floorPlane[0]=new Vector3D(0, Window.getPlanePointCoef(), 2000);
										//floorPlane[1]=new Vector3D(Window.getPlanePointCoef(), Window.getPlanePointCoef(), currentSector.getFloorHeight());
										//floorPlane[2]=new Vector3D(Window.getPlanePointCoef(), 0, currentSector.getFloorHeight());
									} else {
										linedefSpecialsDealtWith[currentseg.getLinedef()]=false;
									}
								} else {
									if(currentLinedef.getAction()==21) { // Floor lower to lowest surrounding floor
									}
								}
							}
						} else {
							switch(currentLinedef.getAction()) {
								case 1: // Use Door. open, wait, close
									sectorTag[otherside.getSector()]=-1;
									break;
								case 31: // Use Door. Open, stay.
									sectorTag[otherside.getSector()]=-2;
									break;
								case 63:
									Entity button=new Entity("func_button");
									MAPBrush buttonBrush = GenericMethods.createFaceBrush("special/trigger", "special/trigger", plane[0], plane[2],0,0);
									button.addBrush(buttonBrush);
									button.setAttribute("target", "sector"+currentLinedef.getTag()+"door");
									button.setAttribute("wait", "1");
									button.setAttribute("spawnflags", "1");
									mapFile.add(button);
									break;
								case 97: // Walkover retriggerable Teleport
									Entity teleporter=new Entity("trigger_teleport");
									MAPBrush teleBrush = GenericMethods.createFaceBrush("special/trigger", "special/trigger", plane[0], plane[2],0,0);
									teleporter.addBrush(teleBrush);
									teleporter.setAttribute("target", "sector"+currentLinedef.getTag()+"teledest");
									mapFile.add(teleporter);
									break;
							}
						}
					}
				}
				
				cielingBrush.add(high);
				midBrush.add(mid);
				floorBrush.add(low);
			}
			
			MAPBrushSide roof=new MAPBrushSide(roofPlane, "special/nodraw", roofTexS, 0, roofTexT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
			MAPBrushSide cieling=new MAPBrushSide(cileingPlane, doomMap.getWadName()+"/"+currentSector.getCielingTexture(), cileingTexS, 0, cileingTexT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
			MAPBrushSide floor=new MAPBrushSide(floorPlane, doomMap.getWadName()+"/"+currentSector.getFloorTexture(), floorTexS, 0, floorTexT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
			MAPBrushSide foundation=new MAPBrushSide(foundationPlane, "special/nodraw", foundationTexS, 0, foundationTexT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
			MAPBrushSide top=new MAPBrushSide(topPlane, "special/nodraw", topTexS, 0, topTexT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
			MAPBrushSide bottom=new MAPBrushSide(bottomPlane, "special/nodraw", bottomTexS, 0, bottomTexT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);

			midBrush.add(top);
			midBrush.add(bottom);
			
			cielingBrush.add(cieling);
			cielingBrush.add(roof);
			
			floorBrush.add(floor);
			floorBrush.add(foundation);
			
			// Now need to add the data from node subdivisions. Neither segments nor nodes
			// will completely define a usable brush, but both of them together will.
			do {
				DNode currentNode=doomMap.getNodes().getNode(nextNode);
				Vector3D start;
				Vector3D end;
				if(leftSide) {
					start=currentNode.getVecHead().add(currentNode.getVecTail());
					end=currentNode.getVecHead();
				} else {
					start=currentNode.getVecHead();
					end=currentNode.getVecHead().add(currentNode.getVecTail());
				}
				
				Vector3D[] plane=new Vector3D[3];
				double[] texS=new double[3];
				double[] texT=new double[3];
				// This is somehow always correct. And I'm okay with that.
				plane[0]=new Vector3D(start.getX(), start.getY(), ZMin);
				plane[1]=new Vector3D(end.getX(), end.getY(), ZMin);
				plane[2]=new Vector3D(start.getX(), start.getY(), ZMax);
				
				double sideLength=Math.sqrt(Math.pow(start.getX()-end.getX(), 2) + Math.pow(start.getY()-end.getY(),2));
				
				texS[0]=(start.getX()-end.getX())/sideLength;
				texS[1]=(start.getY()-end.getY())/sideLength;
				texS[2]=0;
				texT[0]=0;
				texT[1]=0;
				texT[2]=1;
				MAPBrushSide low=new MAPBrushSide(plane, "special/nodraw", texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
				MAPBrushSide high=new MAPBrushSide(plane, "special/nodraw", texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
				MAPBrushSide mid=new MAPBrushSide(plane, "special/nodraw", texS, 0, texT, 0, 0, 1, 1, 0, "wld_lightmap", 16, 0);
				
				cielingBrush.add(high);
				midBrush.add(mid);
				floorBrush.add(low);
				
				leftSide=nodeIsLeft[nextNode];
				nextNode=nodeparents[nextNode];
			} while(nextNode!=-1);
			// Now we need to get rid of all the sides that aren't used. Get a list of
			// the useless sides from one brush, and delete those sides from all of them,
			// since they all have the same sides.
			int[] badSides=new int[0];
			if(!Window.dontCullIsSelected()) {
				badSides=GenericMethods.findUnusedPlanes(cielingBrush);
				// Need to iterate backward, since these lists go from low indices to high, and
				// the index of all subsequent items changes when something before it is removed.
				if(cielingBrush.getNumSides()-badSides.length<4) {
					Window.println("WARNING: Plane cull returned less than 4 sides for subsector "+i,Window.VERBOSITY_WARNINGS);
					badSides=new int[0];
				} else {
					for(int j=badSides.length-1;j>-1;j--) {
						cielingBrush.delete(badSides[j]);
						floorBrush.delete(badSides[j]);
					}
				}
			}
			
			MAPBrush[] newFloorList=new MAPBrush[sectorFloorBrushes[subsectorSectors[i]].length+1];
			MAPBrush[] newCielingList=new MAPBrush[sectorCielingBrushes[subsectorSectors[i]].length+1];
			for(int j=0;j<sectorFloorBrushes[subsectorSectors[i]].length;j++) {
				newFloorList[j]=sectorFloorBrushes[subsectorSectors[i]][j];
				newCielingList[j]=sectorCielingBrushes[subsectorSectors[i]][j];
			}
			newFloorList[newFloorList.length-1]=floorBrush;
			newCielingList[newCielingList.length-1]=cielingBrush;
			sectorFloorBrushes[subsectorSectors[i]]=newFloorList;
			sectorCielingBrushes[subsectorSectors[i]]=newCielingList;
			
			boolean containsMiddle=false;
			for(int j=0;j<midBrush.getNumSides();j++) {
				if(!midBrush.getSide(j).getTexture().equalsIgnoreCase("special/nodraw")) {
					containsMiddle=true;
					break;
				}
			}
			if(containsMiddle && currentSector.getCielingHeight() > currentSector.getFloorHeight()) {
				Entity middleEnt=new Entity("func_illusionary");
				for(int j=badSides.length-1;j>-1;j--) {
					midBrush.delete(badSides[j]);
				}
				
				middleEnt.addBrush(midBrush);
				mapFile.add(middleEnt);
			}
			Window.setProgress(jobnum, i+1, doomMap.getSubSectors().getNumElements(), "Decompiling...");
		}
		
		// Add the brushes to the map, as world by default, or entities if they are supported.
		for(int i=0;i<doomMap.getSectors().getNumElements();i++) {
			if(sectorTag[i]==0) {
				for(int j=0;j<sectorFloorBrushes[i].length;j++) {
					world.addBrush(sectorFloorBrushes[i][j]);
					world.addBrush(sectorCielingBrushes[i][j]);
				}
			} else {
				if(sectorTag[i]==-1 || sectorTag[i]==-2) { // I'm using this to mean a door with no tag number
					Entity newDoor=new Entity("func_door");
					int lowestNeighborCielingHeight=32768;
					for(int j=0;j<doomMap.getLinedefs().length();j++) {
						DLinedef currentLinedef=doomMap.getLinedefs().getElement(j);
						if(!currentLinedef.isOneSided()) {
							DSector neighbor=null;
							if(doomMap.getSidedefs().getSide(currentLinedef.getLeft()).getSector()==i) {
								neighbor=doomMap.getSectors().getSector(doomMap.getSidedefs().getSide(currentLinedef.getRight()).getSector());
							} else {
								if(doomMap.getSidedefs().getSide(currentLinedef.getRight()).getSector()==i) {
									neighbor=doomMap.getSectors().getSector(doomMap.getSidedefs().getSide(currentLinedef.getLeft()).getSector());
								}
							}
							if(neighbor!=null && neighbor.getCielingHeight()<lowestNeighborCielingHeight) {
								lowestNeighborCielingHeight=neighbor.getCielingHeight();
							}
						}
					}
					int lip=ZMax-lowestNeighborCielingHeight+4;
					for(int j=0;j<sectorFloorBrushes[i].length;j++) {
						world.addBrush(sectorFloorBrushes[i][j]);
						newDoor.addBrush(sectorCielingBrushes[i][j]);
						newDoor.setAttribute("speed", "60");
						newDoor.setAttribute("angles", "270 0 0");
						newDoor.setAttribute("spawnflags", "256");
						if(sectorTag[i]==-1) {
							newDoor.setAttribute("wait", "4");
						} else {
							if(sectorTag[i]==-2) {
								newDoor.setAttribute("wait", "-1");
							}
						}
						newDoor.setAttribute("lip", ""+lip);
					}
					mapFile.add(newDoor);
				} else {
					int linedefTriggerType=0;
					for(int j=0;j<doomMap.getLinedefs().length();j++) {
						if(doomMap.getLinedefs().getElement(j).getTag()==sectorTag[i]) {
							linedefTriggerType=doomMap.getLinedefs().getElement(j).getAction();
						}
					}
					switch(linedefTriggerType) {
						default: // I'd like to not use this evenutally, all the trigger types ought to be handled
							Window.println("WARNING: Unimplemented linedef trigger type "+linedefTriggerType+" for sector "+i+" tagged "+sectorTag[i],Window.VERBOSITY_WARNINGS);
							for(int j=0;j<sectorFloorBrushes[i].length;j++) {
								world.addBrush(sectorFloorBrushes[i][j]);
								world.addBrush(sectorCielingBrushes[i][j]);
							}
							break;
					}
				}
			}
		}
		
		// Convert THINGS
		for(int i=0;i<doomMap.getThings().length();i++) {
			DThing currentThing=doomMap.getThings().getElement(i);
			// To find the true height of a thing, I need to iterate through nodes until I come to a subsector
			// definition. Then I need to use the floor height of the sector that subsector belongs to.
			Vector3D origin=currentThing.getOrigin();
			int subsectorIndex=doomMap.getNodes().getNumElements()-1;
			while(subsectorIndex>=0) { // Once child is negative, subsector is found
				DNode currentNode=doomMap.getNodes().getNode(subsectorIndex);
				Vector3D start=currentNode.getVecHead();
				Vector3D end=currentNode.getVecHead().add(currentNode.getVecTail());
				Plane currentPlane=new Plane(start, end, new Vector3D(start.getX(), start.getY(), 1));
				if(currentPlane.distance(origin)<0) {
					subsectorIndex=currentNode.getChild1();
				} else {
					subsectorIndex=currentNode.getChild2();
				}
			}
			subsectorIndex+=32768;
			int sectorIndex=subsectorSectors[subsectorIndex];
			DSector thingSector=doomMap.getSectors().getSector(sectorIndex);
			if(origin.getZ()==0) {
				origin.setZ(thingSector.getFloorHeight());
			}
			
			Entity thing=null;
			// Things from both Doom and Hexen here
			switch(currentThing.getClassNum()) {
				case 1: // Single player spawn
				case 2: // coop
				case 3: // coop
				case 4: // coop
					thing=new Entity("info_player_start");
					if(currentThing.getClassNum()>1) {
						thing.setAttribute("targetname", "coopspawn"+currentThing.getClassNum());
					}
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+(origin.getZ()+36));
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 11: // Deathmatch spawn
					thing=new Entity("info_player_deathmatch");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+(origin.getZ()+36));
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 14: // Teleport destination
					thing=new Entity("info_teleport_destination");
					if(currentThing.getID()!=0) {
						thing.setAttribute("targetname", "teledest"+currentThing.getID());
					} else {
						thing.setAttribute("targetname", "sector"+thingSector.getTag()+"teledest");
					}
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+(origin.getZ()+36));
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 17: // Big cell pack
					thing=new Entity("ammo_bondmine");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 82: // Super shotgun
					thing=new Entity("weapon_pdw90");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2001: // Shotgun
					thing=new Entity("weapon_frinesi");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2002: // Chaingun
					thing=new Entity("weapon_minigun");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2003: // Rocket launcher
					thing=new Entity("weapon_rocketlauncher");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2004: // Plasma gun
					thing=new Entity("weapon_grenadelauncher");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2005: // Chainsaw
					thing=new Entity("weapon_ronin");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2006: // BFG9000
					thing=new Entity("weapon_laserrifle");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2007: // Ammo clip
					thing=new Entity("ammo_pp9");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2008: // Shotgun shells
					thing=new Entity("ammo_mini");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2010: // Rocket
					thing=new Entity("ammo_darts");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2046: // Box of Rockets
					thing=new Entity("ammo_rocketlauncher");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2047: // Cell pack
					thing=new Entity("ammo_grenadelauncher");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2048: // Box of ammo
					thing=new Entity("ammo_mp9");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
				case 2049: // Box of shells
					thing=new Entity("ammo_shotgun");
					thing.setAttribute("origin", origin.getX()+" "+origin.getY()+" "+origin.getZ());
					thing.setAttribute("angles", "0 "+currentThing.getAngle()+" 0");
					break;
			}
			
			if(doomMap.getVersion()==DoomMap.TYPE_HEXEN) { // Hexen only
			
			} else { // Doom only
				
			}
			
			if(thing!=null) {
				mapFile.add(thing);
			}
		}
		
		Entity playerequip=new Entity("game_player_equip");
		playerequip.setAttribute("weapon_pp9", "1");
		mapFile.add(playerequip);
		
		Window.setProgress(jobnum, 1, 1, "Saving...");
		MAPMaker.outputMaps(mapFile, doomMap.getMapName(), doomMap.getFolder()+doomMap.getWadName()+"\\", DoomMap.TYPE_DOOM);
		Date end=new Date();
		Window.println("Time taken: "+(end.getTime()-begin.getTime())+"ms"+(char)0x0D+(char)0x0A,Window.VERBOSITY_ALWAYS);
	}
}

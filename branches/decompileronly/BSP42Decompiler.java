// BSP42Decompiler
// Decompiles a Nightfire BSP

public class BSP42Decompiler {

	// INITIAL DATA DECLARATION AND DEFINITION OF CONSTANTS
	
	public static final int A = 0;
	public static final int B = 1;
	public static final int C = 2;
	
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;
	
	private int jobnum;
	
	private Entities mapFile; // Most MAP file formats (including GearCraft) are simply a bunch of nested entities
	private int numBrshs;
	private int numSimpleCorrects=0;
	private int numAdvancedCorrects=0;
	private int numGoodBrushes=0;
	
	private BSP BSPObject;
	
	// CONSTRUCTORS
	
	// This constructor sets everything according to specified settings.
	public BSP42Decompiler(BSP BSPObject, int jobnum) {
		// Set up global variables
		this.BSPObject=BSPObject;
		this.jobnum=jobnum;
	}
	
	// METHODS
	
	// -decompile()
	// Attempts to convert the Nightfire BSP file back into a .MAP file.
	//
	// This is another one of the most complex things I've ever had to code. I've
	// never nested for loops four deep before.
	// Iterators:
	// i: Current entity in the list
	//  j: Current leaf, referenced in a list by the model referenced by the current entity
	//   k: Current brush, referenced in a list by the current leaf.
	//    l: Current side of the current brush.
	//     m: When attempting vertex decompilation, the current vertex.
	public Entities decompile() throws java.io.IOException, java.lang.InterruptedException {
		Window.println("Decompiling...",Window.VERBOSITY_ALWAYS);
		// In the decompiler, it is not necessary to copy all entities to a new object, since
		// no writing is ever done back to the BSP file.
		mapFile=BSPObject.getEntities();
		int numTotalItems=0;
		// I need to go through each entity and see if it's brush-based.
		// Worldspawn is brush-based as well as any entity with model *#.
		for(int i=0;i<BSPObject.getEntities().size();i++) { // For each entity
			if(Thread.currentThread().interrupted()) {
				throw new java.lang.InterruptedException("while processing entity "+i+".");
			}
			Window.println("Entity "+i+": "+mapFile.getElement(i).getAttribute("classname"),Window.VERBOSITY_ENTITIES);
			// getModelNumber() returns 0 for worldspawn, the *# for brush based entities, and -1 for everything else
			int currentModel=mapFile.getElement(i).getModelNumber();
			
			if(currentModel>-1) { // If this is still -1 then it's strictly a point-based entity. Move on to the next one.
				double[] origin=mapFile.getElement(i).getOrigin();
				int firstLeaf=BSPObject.getModels().getElement(currentModel).getFirstLeaf();
				int numLeaves=BSPObject.getModels().getElement(currentModel).getNumLeaves();
				boolean[] brushesUsed=new boolean[BSPObject.getBrushes().size()]; // Keep a list of brushes already in the model, since sometimes the leaves lump references one brush several times
				numBrshs=0;
				for(int j=0;j<numLeaves;j++) { // For each leaf in the bunch
					Leaf currentLeaf=BSPObject.getLeaves().getElement(j+firstLeaf);
					int firstBrushIndex=currentLeaf.getFirstMarkBrush();
					int numBrushIndices=currentLeaf.getNumMarkBrushes();
					if(numBrushIndices>0) { // A lot of leaves reference no brushes. If this is one, this iteration of the j loop is finished
						for(int k=0;k<numBrushIndices;k++) { // For each brush referenced
							if(Thread.currentThread().interrupted()) {
								throw new java.lang.InterruptedException("while processing entity "+i+" brush "+numBrshs+".");
							}
							if(!brushesUsed[(int)BSPObject.getMarkBrushes().getElement(firstBrushIndex+k)]) { // If the current brush has NOT been used in this entity
								Window.print("Brush "+numBrshs,Window.VERBOSITY_BRUSHCREATION);
								brushesUsed[(int)BSPObject.getMarkBrushes().getElement(firstBrushIndex+k)]=true;
								try {
									decompileBrush(BSPObject.getBrushes().getElement((int)BSPObject.getMarkBrushes().getElement(firstBrushIndex+k)), i); // Decompile the brush
								} catch(java.lang.InterruptedException e) {
									throw new java.lang.InterruptedException("while processing entity "+i+" brush "+numBrshs+" "+e.toString().substring(32));
								}
								numBrshs++;
								numTotalItems++;
								Window.setProgress(jobnum, numTotalItems, BSPObject.getBrushes().size()+BSPObject.getEntities().size(), "Decompiling...");
							}
						}
					}
				}
			}
			numTotalItems++;
			Window.setProgress(jobnum, numTotalItems, BSPObject.getBrushes().size()+BSPObject.getEntities().size(), "Decompiling...");
		}
		if(!Window.skipFlipIsSelected()) {
			Window.println("Num simple corrected brushes: "+numSimpleCorrects,Window.VERBOSITY_MAPSTATS); 
			Window.println("Num advanced corrected brushes: "+numAdvancedCorrects,Window.VERBOSITY_MAPSTATS); 
			Window.println("Num good brushes: "+numGoodBrushes,Window.VERBOSITY_MAPSTATS); 
		}
		return mapFile;
	}
	
	// -decompileBrush(Brush, int, boolean)
	// Decompiles the Brush and adds it to entitiy #currentEntity as .MAP data.
	private void decompileBrush(Brush brush, int currentEntity) throws java.lang.InterruptedException {
		double[] origin=mapFile.getElement(currentEntity).getOrigin();
		int firstSide=brush.getFirstSide();
		int numSides=brush.getNumSides();
		MAPBrushSide[] brushSides=new MAPBrushSide[0];
		boolean isDetail=false;
		if(!Window.noDetailIsSelected() && (brush.getContents()[1] & ((byte)1 << 1)) != 0) {
			isDetail=true;
		}
		MAPBrush mapBrush = new MAPBrush(numBrshs, currentEntity, isDetail);
		int numRealFaces=0;
		boolean containsNonClipSide=false;
		Plane[] brushPlanes=new Plane[0];
		Window.println(": "+numSides+" sides",Window.VERBOSITY_BRUSHCREATION);
		if(mapFile.getElement(currentEntity).getAttribute("classname").equalsIgnoreCase("func_water")) {
			mapBrush.setWater(true);
		}
		for(int l=0;l<numSides;l++) { // For each side of the brush
			if(Thread.currentThread().interrupted()) {
				throw new java.lang.InterruptedException("side "+l+".");
			}
			BrushSide currentSide=BSPObject.getBrushSides().getElement(firstSide+l);
			Face currentFace=BSPObject.getFaces().getElement(currentSide.getFace()); // To find those three points, I can use vertices referenced by faces.
			String texture=BSPObject.getTextures().getElement(currentFace.getTexture()).getName();
			if((currentFace.getFlags()[1] & ((byte)1 << 0)) == 0) { // Surfaceflags 512 + 256 + 32 are set only by the compiler, on faces that need to be thrown out.
				if(!texture.equalsIgnoreCase("special/clip") && !texture.equalsIgnoreCase("special/playerclip") && !texture.equalsIgnoreCase("special/enemyclip")) {
					containsNonClipSide=true;
					if(Window.replaceWithNullIsSelected() && ((currentFace.getFlags()[1] & ((byte)1 << 1)) != 0) && !texture.equalsIgnoreCase("special/trigger")) {
						texture="special/null";
						currentFace.setFlags(new byte[4]);
					}
				}
				int firstVertex=currentFace.getFirstVertex();
				int numVertices=currentFace.getNumVertices();
				Plane currentPlane;
				try { // I've only ever come across this error once or twice, but something causes it very rarely
					currentPlane=BSPObject.getPlanes().getElement(currentSide.getPlane());
				} catch(java.lang.ArrayIndexOutOfBoundsException e) {
					try { // So try to get the plane index from somewhere else
						currentPlane=BSPObject.getPlanes().getElement(currentFace.getPlane());
					}  catch(java.lang.ArrayIndexOutOfBoundsException f) { // If that fails, BS something
						Window.println("WARNING: BSP has error, references nonexistant plane "+currentSide.getPlane()+", bad side "+(l)+" of brush "+numBrshs+" Entity "+currentEntity,Window.VERBOSITY_WARNINGS);
						currentPlane=new Plane((double)1, (double)0, (double)0, (double)0);
					}
				}
				Vector3D[] triangle=new Vector3D[0];
				boolean pointsWorked=false;
				if(numVertices!=0 && !Window.planarDecompIsSelected()) { // If the face actually references a set of vertices
					triangle=new Vector3D[3]; // Three points define a plane. All I have to do is find three points on that plane.
					triangle[0]=new Vector3D(BSPObject.getVertices().getElement(firstVertex).getVertex()); // Grab and store the first one
					int m=1;
					for(m=1;m<numVertices;m++) { // For each point after the first one
						triangle[1]=new Vector3D(BSPObject.getVertices().getElement(firstVertex+m).getVertex());
						if(!triangle[0].equals(triangle[1])) { // Make sure the point isn't the same as the first one
							break; // If it isn't the same, this point is good
						}
					}
					for(m=m+1;m<numVertices;m++) { // For each point after the previous one used
						triangle[2]=new Vector3D(BSPObject.getVertices().getElement(firstVertex+m).getVertex());
						if(!triangle[2].equals(triangle[0]) && !triangle[2].equals(triangle[1])) { // Make sure no point is equal to the third one
							// Make sure all three points are non collinear
							Vector3D cr=Vector3D.crossProduct(triangle[0].subtract(triangle[1]), triangle[0].subtract(triangle[2]));
							if(cr.length() > Window.getPrecision()) { // vector length is never negative.
								pointsWorked=true;
								break;
							}
						}
					}
				}
				double[] textureU=new double[3];
				double[] textureV=new double[3];
				TexInfo currentTexInfo=BSPObject.getTexInfo().getElement(currentFace.getTextureScale());
				// Get the lengths of the axis vectors
				double SAxisLength=Math.sqrt(Math.pow((double)currentTexInfo.getSAxis().getX(),2)+Math.pow((double)currentTexInfo.getSAxis().getY(),2)+Math.pow((double)currentTexInfo.getSAxis().getZ(),2));
				double TAxisLength=Math.sqrt(Math.pow((double)currentTexInfo.getTAxis().getX(),2)+Math.pow((double)currentTexInfo.getTAxis().getY(),2)+Math.pow((double)currentTexInfo.getTAxis().getZ(),2));
				// In compiled maps, shorter vectors=longer textures and vice versa. This will convert their lengths back to 1. We'll use the actual scale values for length.
				double texScaleU=(1/SAxisLength);// Let's use these values using the lengths of the U and V axes we found above.
				double texScaleV=(1/TAxisLength);
				textureU[0]=((double)currentTexInfo.getSAxis().getX()/SAxisLength);
				textureU[1]=((double)currentTexInfo.getSAxis().getY()/SAxisLength);
				textureU[2]=((double)currentTexInfo.getSAxis().getZ()/SAxisLength);
				double originShiftU=(textureU[0]*origin[X]+textureU[1]*origin[Y]+textureU[2]*origin[Z])/texScaleU;
				double textureUhiftU=(double)currentTexInfo.getSShift()-originShiftU;
				textureV[0]=((double)currentTexInfo.getTAxis().getX()/TAxisLength);
				textureV[1]=((double)currentTexInfo.getTAxis().getY()/TAxisLength);
				textureV[2]=((double)currentTexInfo.getTAxis().getZ()/TAxisLength);
				double originShiftV=(textureV[0]*origin[X]+textureV[1]*origin[Y]+textureV[2]*origin[Z])/texScaleV;
				double textureUhiftV=(double)currentTexInfo.getTShift()-originShiftV;
				float texRot=0; // In compiled maps this is calculated into the U and V axes, so set it to 0 until I can figure out a good way to determine a better value.
				int flags=DataReader.readInt(currentFace.getFlags()[0], currentFace.getFlags()[1], currentFace.getFlags()[2], currentFace.getFlags()[3]); // This is actually a set of flags. Whatever.
				String material;
				try {
					material=BSPObject.getMaterials().getElement(currentFace.getMaterial()).getName();
				} catch(java.lang.ArrayIndexOutOfBoundsException e) { // In case the BSP has some strange error making it reference nonexistant materials
					Window.println("WARNING: Map referenced nonexistant material #"+currentFace.getMaterial()+", using wld_lightmap instead!",Window.VERBOSITY_WARNINGS);
					material="wld_lightmap";
				}
				double lgtScale=16; // These values are impossible to get from a compiled map since they
				double lgtRot=0;    // are used by RAD for generating lightmaps, then are discarded, I believe.
				MAPBrushSide[] newList=new MAPBrushSide[brushSides.length+1];
				for(int i=0;i<brushSides.length;i++) {
					newList[i]=brushSides[i];
				}
				if(Window.noFaceFlagsIsSelected()) {
					flags=0;
				}
				if(pointsWorked) {
					newList[brushSides.length]=new MAPBrushSide(currentPlane, triangle, texture, textureU, textureUhiftU, textureV, textureUhiftV,
					                                            texRot, texScaleU, texScaleV, flags, material, lgtScale, lgtRot);
				} else {
					newList[brushSides.length]=new MAPBrushSide(currentPlane, texture, textureU, textureUhiftU, textureV, textureUhiftV,
					                                            texRot, texScaleU, texScaleV, flags, material, lgtScale, lgtRot);
				}
				brushSides=newList;
				numRealFaces++;
			}
		}
		
		for(int i=0;i<brushSides.length;i++) {
			mapBrush.add(brushSides[i]);
		}
		
		brushPlanes=new Plane[mapBrush.getNumSides()];
		for(int i=0;i<brushPlanes.length;i++) {
			brushPlanes[i]=mapBrush.getSide(i).getPlane();
		}
		
		if(!Window.skipFlipIsSelected()) {
			if(mapBrush.hasBadSide()) { // If there's a side that might be backward
				if(mapBrush.hasGoodSide()) { // If there's a side that is forward
					mapBrush=MAPBrush.SimpleCorrectPlanes(mapBrush);
					numSimpleCorrects++;
					if(Window.calcVertsIsSelected()) { // This is performed in advancedcorrect, so don't use it if that's happening
						try {
							mapBrush=MAPBrush.CalcBrushVertices(mapBrush);
						} catch(java.lang.NullPointerException e) {
							Window.println("WARNING: Brush vertex calculation failed on entity "+mapBrush.getEntnum()+" brush "+mapBrush.getBrushnum()+"",Window.VERBOSITY_WARNINGS);
						}
					}
				} else { // If no forward side exists
					try {
						mapBrush=MAPBrush.AdvancedCorrectPlanes(mapBrush);
						numAdvancedCorrects++;
					} catch(java.lang.ArithmeticException e) {
						Window.println("WARNING: Plane correct returned 0 triangles for entity "+mapBrush.getEntnum()+" brush "+mapBrush.getBrushnum()+"",Window.VERBOSITY_WARNINGS);
					}
				}
			} else {
				numGoodBrushes++;
			}
		} else {
			if(Window.calcVertsIsSelected()) { // This is performed in advancedcorrect, so don't use it if that's happening
				try {
					mapBrush=MAPBrush.CalcBrushVertices(mapBrush);
				} catch(java.lang.NullPointerException e) {
					Window.println("WARNING: Brush vertex calculation failed on entity "+mapBrush.getEntnum()+" brush "+mapBrush.getBrushnum()+"",Window.VERBOSITY_WARNINGS);
				}
			}
		}
		
		// This adds the brush we've been finding and creating to
		// the current entity as an attribute. The way I've coded
		// this whole program and the entities parser, this shouldn't
		// cause any issues at all.
		if(Window.brushesToWorldIsSelected()) {
			mapBrush.setWater(false);
			mapFile.getElement(0).addBrush(mapBrush);
		} else {
			mapFile.getElement(currentEntity).addBrush(mapBrush);
		}
	}
}

/**
 * 
 */
package com.wildplot.android.rendering;

import com.wildplot.android.rendering.graphics.wrapper.*;
import com.wildplot.android.rendering.interfaces.Drawable;

import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;


/**
 * This is a sheet that is used to plot mathematical functions including coordinate systems and optional extras like 
 * legends and descriptors. Additionally all conversions from image to plot coordinates are done here
 */
public class AdvancedPlotSheet extends PlotSheet implements Runnable{
	
	private ReentrantLock reentrantLock = new ReentrantLock();
	private boolean pictureIsConstructed = false;
	private BufferedImage plotImage = null;
	private boolean operationIsAborted = false;
	private Rectangle field;

	private boolean hasFirstPixelSkipSet = false;
	private float firstPixelSkip = 20;
	private ReliefDrawer reliefDrawer = null;
	

	/**
	 * Create a virtual sheet used for the plot
	 * @param xStart the start of the x-range
	 * @param xEnd the end of the x-range
	 * @param yStart the start of the y-range
	 * @param yEnd the end of the y-range
	 * @param drawables list of Drawables that shall be drawn onto the sheet
	 */
	public AdvancedPlotSheet(double xStart, double xEnd, double yStart, double yEnd, Vector<Drawable> drawables) {
		super(xStart, xEnd, yStart, yEnd, drawables);
		double[] xRange = {xStart, xEnd};
		double[] yRange = {yStart, yEnd};
		screenParts.add(0, new MultiScreenPart(xRange, yRange, drawables));
	}
	
	/**
	 * 
	 * Create a virtual sheet used for the plot
	 * @param xStart the start of the x-range
	 * @param xEnd the end of the x-range
	 * @param yStart the start of the y-range
	 * @param yEnd the end of the y-range
	 */
	public AdvancedPlotSheet(double xStart, double xEnd, double yStart, double yEnd, BufferedImage plotImage) {
		super(xStart, xEnd, yStart, yEnd);
		double[] xRange = {xStart, xEnd};
		double[] yRange = {yStart, yEnd};
		screenParts.add(0, new MultiScreenPart(xRange, yRange));
        this.plotImage = plotImage;
		
	}
	
	public void setClip(Rectangle field){
		this.field = field;
	}
	
	@Override
	public void run() {
        prepareRunnables();
		operationIsAborted = false;
		pictureIsConstructed = false;
		HashMap<Thread, DrawableDrawingRunnable> allDrawableDirectory = new HashMap<Thread, DrawableDrawingRunnable>();
		this.currentScreen = 0;
		
		Vector<DrawableDrawingRunnable> offFrameDrawables = new Vector<DrawableDrawingRunnable>();
		Vector<DrawableDrawingRunnable> onFrameDrawables = new Vector<DrawableDrawingRunnable>();
		//System.err.println("APlotSheet: field: " + field.width + " : " + field.height);
		BufferedImage bufferedFrameImage = new BufferedImage(field.width, field.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gFrame = bufferedFrameImage.createGraphics();
		gFrame.setClip(field);
		gFrame.setColor(Color.BLACK);
		Thread[] threads = new Thread[this.screenParts.get(currentScreen).getDrawables().size()]; 
		int i = 0;
		
		if(this.screenParts.get(currentScreen).getDrawables() != null && this.screenParts.get(currentScreen).getDrawables().size() != 0) {
			for(Drawable draw : this.screenParts.get(currentScreen).getDrawables()) {
				DrawableDrawingRunnable drawableDrawingRunnable = new DrawableDrawingRunnable(draw, field);
				threads[i] = new Thread( drawableDrawingRunnable);
				allDrawableDirectory.put(threads[i],drawableDrawingRunnable );
				threads[i++].start();
				if(!draw.isOnFrame()) {
					offFrameDrawables.add(drawableDrawingRunnable);
				} else {
					onFrameDrawables.add(drawableDrawingRunnable);
				}
			}
		}
		
		//paint white frame to over paint everything that was drawn over the border 
		Color oldColor = gFrame.getColor();
		if(this.frameThickness>0){
			gFrame.setColor(Color.WHITE);
			//upper frame
			gFrame.fillRect(0, 0, field.width, this.frameThickness);

			//left frame
			gFrame.fillRect(0, this.frameThickness, this.frameThickness, field.height);
			
			//right frame
			gFrame.fillRect(field.width+1-this.frameThickness, this.frameThickness,this.frameThickness+2, field.height-this.frameThickness);
			
			//bottom frame
			gFrame.fillRect(this.frameThickness, field.height-this.frameThickness, field.width-this.frameThickness,this.frameThickness+1);
			
			//make small black border frame
			if(isBordered){
				gFrame.setColor(Color.black);
				//upper border
				gFrame.fillRect(this.frameThickness-borderThickness+1, this.frameThickness-borderThickness+1, field.width-2*this.frameThickness+2*borderThickness-2, borderThickness);
				
				//lower border
				gFrame.fillRect(this.frameThickness-borderThickness+1, field.height-this.frameThickness, field.width-2*this.frameThickness+2*borderThickness-2, borderThickness);
				
				//left border
				gFrame.fillRect(this.frameThickness-borderThickness+1, this.frameThickness-borderThickness+1, borderThickness, field.height-2*this.frameThickness+2*borderThickness-2);
				
				//right border
				gFrame.fillRect(field.width-this.frameThickness, this.frameThickness-borderThickness+1, borderThickness, field.height-2*this.frameThickness+2*borderThickness-2);
				
			}
			
			gFrame.setColor(oldColor);
			
//			Font oldFont = gFrame.getFont();
//			gFrame.setFont(oldFont.deriveFont(20.0f));
			FontMetrics fm = gFrame.getFontMetrics();
            float height = fm.getHeight();

            float width = fm.stringWidth(this.title);
			gFrame.drawString(this.title, field.width/2 -width/2, this.frameThickness - 10 - height);
//			gFrame.setFont(oldFont);
		}
		gFrame.dispose();
		
		boolean finished = true;
		do{
			//TODO give this abort message to all drawingRunnable for them to handle this event
			if(operationIsAborted){
				if(hasFirstPixelSkipSet){
					this.reliefDrawer.setPixelSkip(firstPixelSkip);
					this.reliefDrawer.abortAndReset();
				}
				for(Drawable draw : this.screenParts.get(currentScreen).getDrawables()) {
					draw.abortAndReset();
				}
				for(i=0; i<threads.length; i++ ){
					try {
						threads[i].join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				return;
			}
			
			finished = true;
			for(i=0; i<threads.length; i++ ){
				DrawableDrawingRunnable currentDrawableRunnable = allDrawableDirectory.get(threads[i]);
				if (currentDrawableRunnable.hasFinished() || currentDrawableRunnable.getIsCritical()) {
					if(!currentDrawableRunnable.hasJoined()){
						Drawable drawable = currentDrawableRunnable.getDrawable();
						if(drawable instanceof ReliefDrawer){
							reliefDrawer = (ReliefDrawer)drawable;
							if(!this.hasFirstPixelSkipSet){
								this.firstPixelSkip = reliefDrawer.getPixelSkip();
								hasFirstPixelSkipSet = true;
							}
							if(reliefDrawer.getPixelSkip() == 1){
								try {
									threads[i].join();
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								currentDrawableRunnable.hasJoined = true;
							} else {
								reliefDrawer.setPixelSkip(reliefDrawer.getPixelSkip() -1);
								try {
									threads[i].join();
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								BufferedImage oldReliefImage = currentDrawableRunnable.getBufferedDrawableImage();
								currentDrawableRunnable.setBufferedOldDrawableImage(oldReliefImage);
								threads[i] = new Thread(currentDrawableRunnable);
								threads[i].start();
								allDrawableDirectory.put(threads[i], currentDrawableRunnable);
								finished = false;
							}
							
						}else{
							try {
								threads[i].join();
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							currentDrawableRunnable.hasJoined = true;
						}
					}
					
				} else {
					finished = false;
				}
			}
			BufferedImage buffTempImage = new BufferedImage(field.width, field.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g =  buffTempImage.createGraphics();
			g.setClip(field);
			
			for(DrawableDrawingRunnable offFrameDrawable : offFrameDrawables){
				if(offFrameDrawable.hasJoined)
					g.drawImage(offFrameDrawable.getBufferedDrawableImage(), null, 0, 0);
				else if(offFrameDrawable.getBufferedOldDrawableImage() != null)
					g.drawImage(offFrameDrawable.getBufferedOldDrawableImage(), null, 0, 0);
				
					
			}
			g.drawImage(bufferedFrameImage, null, 0, 0);
			for(DrawableDrawingRunnable onFrameDrawable : onFrameDrawables){
				if(onFrameDrawable.hasJoined)
					g.drawImage(onFrameDrawable.getBufferedDrawableImage(), null, 0, 0);
				else if(onFrameDrawable.getBufferedOldDrawableImage() != null)
					g.drawImage(onFrameDrawable.getBufferedOldDrawableImage(), null, 0, 0);
			}
			g.dispose();
			
			reentrantLock.lock();
			this.plotImage = buffTempImage;
			reentrantLock.unlock();
			
		}while(!finished);
		if(hasFirstPixelSkipSet){
			this.reliefDrawer.setPixelSkip(firstPixelSkip);
		}
		pictureIsConstructed = true;
		
	}

    private void prepareRunnables(){
        Vector<Drawable> drawables = this.screenParts.get(0).getDrawables();
        Vector<Drawable> onFrameDrawables = new Vector<Drawable>();
        Vector<Drawable> offFrameDrawables = new Vector<Drawable>();

        DrawableContainer onFrameContainer = new DrawableContainer(true, false);

        DrawableContainer offFrameContainer = new DrawableContainer(false, false);
        for(Drawable drawable : drawables){
            if(drawable.isOnFrame()){
                if(drawable.isClusterable()){
                    if(onFrameContainer.isCritical() == drawable.isCritical()){
                        onFrameContainer.addDrawable(drawable);
                    }else {
                        if(onFrameContainer.getSize() > 0) {
                            onFrameDrawables.add(onFrameContainer);
                        }
                        onFrameContainer = new DrawableContainer(true, drawable.isCritical());
                        onFrameContainer.addDrawable(drawable);
                    }
                }else{
                    if(onFrameContainer.getSize() > 0) {
                        onFrameDrawables.add(onFrameContainer);
                    }
                    onFrameDrawables.add(drawable);
                    onFrameContainer = new DrawableContainer(true, false);

                }
            }else{
                if(drawable.isClusterable()){
                    if(offFrameContainer.isCritical() == drawable.isCritical()){
                        offFrameContainer.addDrawable(drawable);
                    } else {
                        if(offFrameContainer.getSize() > 0){
                            offFrameDrawables.add(offFrameContainer);
                        }
                        offFrameContainer = new DrawableContainer(false, drawable.isCritical());
                        offFrameContainer.addDrawable(drawable);
                    }
                }else{
                    if(offFrameContainer.getSize() > 0){
                        offFrameDrawables.add(offFrameContainer);
                    }
                    offFrameDrawables.add(drawable);
                    offFrameContainer = new DrawableContainer(false, false);
                }
            }
        }
        if(onFrameContainer.getSize() > 0) {
            onFrameDrawables.add(onFrameContainer);
        }

        if(offFrameContainer.getSize() > 0){
            offFrameDrawables.add(offFrameContainer);
        }

        this.screenParts.get(0).getDrawables().removeAllElements();
        this.screenParts.get(0).getDrawables().addAll(offFrameDrawables);
        this.screenParts.get(0).getDrawables().addAll(onFrameDrawables);


    }


	public void abortOperation(){
		this.operationIsAborted = true;
	}
	
	/**
	 * update the x-Range of this PlotSheet
	 * @param xStart left beginning of plot
	 * @param xEnd right end of plot
	 */
    @Override
	public void updateX(double xStart, double xEnd) {
    	double[] xRange = {xStart, xEnd};
        this.screenParts.get(0).setxRange(xRange);
    }
    
    /**
	 * update the y-Range of this PlotSheet
	 * @param yStart bottom beginning of plot
	 * @param yEnd upper end of plot
	 */
    @Override
	public void updateY(double yStart, double yEnd) {
    	double[] yRange = {yStart, yEnd};
        this.screenParts.get(0).setyRange(yRange);
    }
	
	/**
	 * add another Drawable object that shall be drawn onto the sheet
	 * this adds only drawables for the first screen in multimode plots for
	 * 
	 * @param draw Drawable object which will be addet to plot sheet
	 */
	@Override
	public void addDrawable(Drawable draw) {
		this.screenParts.get(0).addDrawable(draw);
	}
	



	


    @Override
    public boolean isClusterable() {
        return false;
    }


	public BufferedImage getPlotImage(){
		reentrantLock.lock();
		try{
			return plotImage;
		} finally {
			reentrantLock.unlock();
		}
	}
	
	public boolean isFinished(){
		return pictureIsConstructed;
	}
	
	private class DrawableDrawingRunnable implements Runnable {
		
		private Drawable drawable;
		private boolean hasFinished = false;
		private boolean hasJoined = false;
		
		private BufferedImage bufferedDrawableImage;
		private BufferedImage bufferedOldDrawableImage = null;
		private Rectangle field;

		public DrawableDrawingRunnable(Drawable drawable, Rectangle field) {
			super();
			this.drawable = drawable;
			this.field = field;
			
		}


		@Override
		public void run() {
		    this.hasFinished = false;
			bufferedDrawableImage = new BufferedImage(field.width, field.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = bufferedDrawableImage.createGraphics();
			g.setClip(field);
			g.setColor(Color.BLACK);
			drawable.paint(g);
			g.dispose();
			this.hasFinished = true;
			
		}

		public Drawable getDrawable() {
			return drawable;
		}

		public boolean hasFinished(){
			return hasFinished;
		}
		public boolean hasJoined(){
			return hasJoined;
		}
		public BufferedImage getBufferedDrawableImage() {
			return bufferedDrawableImage;
		}


		public BufferedImage getBufferedOldDrawableImage() {
			return bufferedOldDrawableImage;
		}


		public void setBufferedOldDrawableImage(BufferedImage bufferedOldDrawableImage) {
			this.bufferedOldDrawableImage = bufferedOldDrawableImage;
		}

        public boolean getIsCritical(){
            return drawable.isCritical();
        }
		
		
	}

	
}

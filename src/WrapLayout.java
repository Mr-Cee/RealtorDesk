import java.awt.*;

/** Toolbars grow vertically when the window is narrow instead of clipping actions. */
final class WrapLayout extends FlowLayout {
    WrapLayout(int alignment,int hgap,int vgap){super(alignment,hgap,vgap);}
    public Dimension preferredLayoutSize(Container target){return size(target,false);}
    public Dimension minimumLayoutSize(Container target){var d=size(target,true);d.width=160;return d;}
    private Dimension size(Container target,boolean minimum){synchronized(target.getTreeLock()){
        int width=target.getWidth();for(Container p=target.getParent();width<=0&&p!=null;p=p.getParent())width=p.getWidth();if(width<=0)return super.preferredLayoutSize(target);
        Insets in=target.getInsets();int available=Math.max(1,width-in.left-in.right-getHgap()*2),rowWidth=0,rowHeight=0,totalHeight=0,maxWidth=0;
        for(Component c:target.getComponents())if(c.isVisible()){Dimension d=minimum?c.getMinimumSize():c.getPreferredSize();int gap=rowWidth==0?0:getHgap();if(rowWidth>0&&rowWidth+gap+d.width>available){maxWidth=Math.max(maxWidth,rowWidth);totalHeight+=rowHeight+getVgap();rowWidth=0;rowHeight=0;gap=0;}rowWidth+=gap+d.width;rowHeight=Math.max(rowHeight,d.height);}
        return new Dimension(Math.max(maxWidth,rowWidth)+in.left+in.right+2*getHgap(),totalHeight+rowHeight+in.top+in.bottom+2*getVgap());
    }}
}

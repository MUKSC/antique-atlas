package folk.sisby.antique_atlas.gui.core;

/**
 * A button that can be toggled on, and only toggled off by selecting
 * a different ToggleButton.
 */
public class ToggleButtonComponent extends ButtonComponent {
    private boolean selected;

    /**
     * Sets the button selected state. If the button is part of a RadioGroup,
     * use the RadioGroup's setSelected method instead!
     */
    public void setSelected(boolean value) {
        this.selected = value;
    }

    public boolean isSelected() {
        return selected;
    }

    @Override
    protected void onClick() {
        if (isEnabled()) {
            setSelected(!isSelected());
        }
        super.onClick();
    }
}

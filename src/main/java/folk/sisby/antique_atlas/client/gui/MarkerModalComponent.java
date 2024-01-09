package folk.sisby.antique_atlas.client.gui;

import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.client.api.AtlasClientAPI;
import folk.sisby.antique_atlas.client.gui.core.Component;
import folk.sisby.antique_atlas.client.gui.core.ScrollBoxComponent;
import folk.sisby.antique_atlas.client.gui.core.ToggleButtonRadioGroup;
import folk.sisby.antique_atlas.client.MarkerType;
import folk.sisby.antique_atlas.client.resource.MarkerTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * This GUI is used select marker icon and enter a label.
 * When the user clicks on the confirmation button, the call to MarkerAPI is made.
 *
 * @author Hunternif
 */
public class MarkerModalComponent extends Component {
    private World world;
    private int atlasID;
    private int markerX;
    private int markerZ;

    MarkerType selectedType = MarkerTypes.REGISTRY.get(MarkerTypes.REGISTRY.getDefaultId());

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_SPACING = 4;

    private static final int TYPE_SPACING = 1;
    private static final int TYPE_BG_FRAME = 4;

    private ButtonWidget btnDone;
    private ButtonWidget btnCancel;
    private TextFieldWidget textField;
    private ScrollBoxComponent scroller;
    private ToggleButtonRadioGroup<MarkerTypeSelectorComponent> typeRadioGroup;

    private final List<IMarkerTypeSelectListener> markerListeners = new ArrayList<>();

    MarkerModalComponent() {
    }

    void setMarkerData(World world, int atlasID, int markerX, int markerZ) {
        this.world = world;
        this.atlasID = atlasID;
        this.markerX = markerX;
        this.markerZ = markerZ;
    }

    void addMarkerListener(IMarkerTypeSelectListener listener) {
        markerListeners.add(listener);
    }

    @Override
    public void init() {
        super.init();

        addDrawableChild(btnDone = ButtonWidget.builder(Text.translatable("gui.done"), (button) -> {
            AtlasClientAPI.getMarkerAPI().putMarker(world, true, atlasID, MarkerTypes.REGISTRY.getId(selectedType), Text.literal(textField.getText()), markerX, markerZ);
            AntiqueAtlas.LOG.info("Put marker in Atlas #{} \"{}\" at ({}, {})", atlasID, textField.getText(), markerX, markerZ);

            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            world.playSound(player, player.getBlockPos(),
                SoundEvents.ENTITY_VILLAGER_WORK_CARTOGRAPHER, SoundCategory.AMBIENT,
                1F, 1F);
            closeChild();
        }).dimensions(this.width / 2 - BUTTON_WIDTH - BUTTON_SPACING / 2, this.height / 2 + 40, BUTTON_WIDTH, 20).build());
        addDrawableChild(btnCancel = ButtonWidget.builder(Text.translatable("gui.cancel"), (button) -> closeChild())
            .dimensions(this.width / 2 + BUTTON_SPACING / 2, this.height / 2 + 40, BUTTON_WIDTH, 20).build());
        textField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, (this.width - 200) / 2, this.height / 2 - 81, 200, 20, Text.translatable("gui.antique_atlas.marker.label"));
        textField.setEditable(true);
        textField.setFocusUnlocked(true);
        textField.setFocused(true);

        scroller = new ScrollBoxComponent();
        scroller.setWheelScrollsHorizontally();
        this.addChild(scroller);

        int typeCount = 0;
        for (MarkerType type : MarkerTypes.REGISTRY) {
            if (!type.isTechnical())
                typeCount++;
        }
        int allTypesWidth = typeCount *
            (MarkerTypeSelectorComponent.FRAME_SIZE + TYPE_SPACING) - TYPE_SPACING;
        int scrollerWidth = Math.min(allTypesWidth, 240);
        scroller.setViewportSize(scrollerWidth, MarkerTypeSelectorComponent.FRAME_SIZE + TYPE_SPACING);
        scroller.setGuiCoords((this.width - scrollerWidth) / 2, this.height / 2 - 25);

        typeRadioGroup = new ToggleButtonRadioGroup<>();
        typeRadioGroup.addListener(button -> {
            selectedType = button.getMarkerType();
            for (IMarkerTypeSelectListener listener : markerListeners) {
                listener.onSelectMarkerType(button.getMarkerType());
            }
        });
        int contentX = 0;
        for (MarkerType markerType : MarkerTypes.REGISTRY) {
            if (markerType.isTechnical())
                continue;
            MarkerTypeSelectorComponent markerGui = new MarkerTypeSelectorComponent(markerType);
            typeRadioGroup.addButton(markerGui);
            if (selectedType.equals(markerType)) {
                typeRadioGroup.setSelectedButton(markerGui);
            }
            scroller.addContent(markerGui).setRelativeX(contentX);
            contentX += MarkerTypeSelectorComponent.FRAME_SIZE + TYPE_SPACING;
        }
    }

    @Override
    public void closeChild() {
        super.closeChild();
        if (scroller != null) {
            scroller.closeChild();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || textField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int aa, int bb, int cc) {
        return super.keyPressed(aa, bb, cc) || textField.keyPressed(aa, bb, cc);
    }

    @Override
    public boolean charTyped(char aa, int bb) {
        return super.charTyped(aa, bb) || textField.charTyped(aa, bb);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(context);
        drawCentered(context, Text.translatable("gui.antique_atlas.marker.label"), this.height / 2 - 97, 0xffffff, true);
        textField.render(context, mouseX, mouseY, partialTick);
        drawCentered(context, Text.translatable("gui.antique_atlas.marker.type"), this.height / 2 - 44, 0xffffff, true);

        // Darker background for marker type selector
        context.fillGradient(scroller.getGuiX() - TYPE_BG_FRAME, scroller.getGuiY() - TYPE_BG_FRAME,
            scroller.getGuiX() + scroller.getWidth() + TYPE_BG_FRAME,
            scroller.getGuiY() + scroller.getHeight() + TYPE_BG_FRAME,
            0x88101010, 0x99101010);
        super.render(context, mouseX, mouseY, partialTick);
    }

    interface IMarkerTypeSelectListener {
        void onSelectMarkerType(MarkerType markerType);
    }
}
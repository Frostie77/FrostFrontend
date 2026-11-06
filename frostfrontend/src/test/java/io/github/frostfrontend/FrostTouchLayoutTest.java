package io.github.frostfrontend;

import static org.junit.Assert.*;
import android.content.Context;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "mdpi")
public class FrostTouchLayoutTest {
    private Context context;

    private static class Controls extends FrostTouchControls {
        int gameplayTouches, releases;
        Controls(Context context, String profile, String... ids) { super(context, profile, ids); }
        @Override protected void computeLayout() {
            for (int i = 0; i < centerX.length; i++) positionControl(i, 120 + i * 140, 300, 25, 35);
        }
        @Override protected void drawControls(Canvas canvas) {}
        @Override protected boolean onGameplayTouch(MotionEvent event) { gameplayTouches++; return true; }
        @Override protected void releaseGameKeys() { releases++; }
        float[] center(int id) { computeLayout(); return new float[]{centerX[id], centerY[id]}; }
    }

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        for (String id : new String[]{"one", "two", "menu"})
            context.getSharedPreferences("frost_touch_layout_" + id, Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static void touch(Controls controls, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0, 0, action, x, y, 0);
        try { controls.onTouchEvent(event); } finally { event.recycle(); }
    }

    private static void drag(Controls controls, int id, float x, float y) {
        float[] old = controls.center(id);
        touch(controls, MotionEvent.ACTION_DOWN, old[0], old[1]);
        touch(controls, MotionEvent.ACTION_MOVE, x, y);
        touch(controls, MotionEvent.ACTION_UP, x, y);
    }

    private Controls controls(String profile, String... ids) {
        Controls controls = new Controls(context, profile, ids);
        controls.layout(0, 0, 1000, 600);
        return controls;
    }

    @Test public void arbitraryIdsPersistAcrossReorderingAndProfilesStayIndependent() {
        Controls first = controls("one", "orbit", "interact", "dash");
        first.beginEditing(); drag(first, 0, 650, 240); first.finishEditing(true);
        Controls reordered = controls("one", "dash", "orbit", "interact");
        assertArrayEquals(new float[]{650, 240}, reordered.center(1), 0.01f);
        assertFalse(controls("two", "orbit", "interact", "dash").hasCustomLayout());
        assertEquals(0, first.gameplayTouches);
    }

    private FrostFrontendView frontend(FrameLayout root, Controls controls, List<Boolean> visibility) {
        FrostFrontendView frontend = new FrostFrontendView(context);
        root.addView(frontend, new FrameLayout.LayoutParams(-1, -1));
        frontend.setTabs(Collections.singletonList(new FrostFrontendView.Tab("touch", "Touch Settings")));
        frontend.setAdapter(new FrostFrontendView.Adapter() {
            @Override public List<FrostFrontendView.Item> itemsForTab(String tab) { return Collections.emptyList(); }
            @Override public void onItemSelected(String tab, String id) { fail("Built-in layout action leaked to host"); }
            @Override public void onVisibilityChanged(boolean visible) { visibility.add(visible); }
        });
        if (controls != null) frontend.setTouchControls(controls);
        return frontend;
    }

    @Test public void registeredEditorIsAutomaticAndRestoresParentWithoutUnpausing() throws Exception {
        FrameLayout root = new FrameLayout(context);
        Controls controls = controls("menu", "move", "jump");
        root.addView(controls, new FrameLayout.LayoutParams(-1, -1));
        controls.setVisibility(View.GONE);
        List<Boolean> visibility = new ArrayList<>();
        FrostFrontendView frontend = frontend(root, controls, visibility);
        frontend.open();
        Field items = FrostFrontendView.class.getDeclaredField("items"); items.setAccessible(true);
        List<?> rows = (List<?>) items.get(frontend);
        assertEquals(FrostFrontendView.TOUCH_LAYOUT_ITEM_ID, ((FrostFrontendView.Item) rows.get(0)).id);
        frontend.handleKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
        assertFalse(frontend.isEditingTouchLayout());
        frontend.handleKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A));
        assertTrue(frontend.isOpen()); assertTrue(frontend.isEditingTouchLayout());
        assertEquals(View.VISIBLE, controls.getVisibility());
        touch(controls, MotionEvent.ACTION_DOWN, 900, 150);
        touch(controls, MotionEvent.ACTION_UP, 900, 150);
        assertEquals(0, controls.gameplayTouches);
        frontend.back();
        assertFalse(frontend.isEditingTouchLayout()); assertTrue(frontend.isOpen());
        assertSame(root, controls.getParent()); assertEquals(0, root.indexOfChild(controls));
        assertEquals(View.GONE, controls.getVisibility());
        assertEquals(Collections.singletonList(true), visibility);
        frontend.close();
        assertEquals(java.util.Arrays.asList(true, false), visibility);
    }

    @Test public void sharedSaveCancelResetAndCloseRetainTheirContracts() {
        FrameLayout root = new FrameLayout(context);
        Controls controls = controls("menu", "move", "dash");
        root.addView(controls);
        FrostFrontendView frontend = frontend(root, controls, new ArrayList<>());
        frontend.open(); frontend.editTouchLayout();
        controls.layout(0, 0, 1000, 600);
        drag(controls, 1, 600, 250);
        frontend.handleKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START));
        assertTrue(controls.hasCustomLayout()); assertTrue(frontend.isOpen());
        frontend.editTouchLayout(); controls.resetLayout();
        frontend.handleKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B));
        assertTrue(controls.hasCustomLayout());
        assertArrayEquals(new float[]{600, 250}, controls.center(1), 0.01f);
        frontend.editTouchLayout(); drag(controls, 1, 400, 200); frontend.close();
        assertSame(root, controls.getParent()); assertFalse(controls.isEditing());
        assertArrayEquals(new float[]{600, 250}, controls.center(1), 0.01f);
        frontend.open(); frontend.editTouchLayout(); controls.resetLayout(); frontend.finishTouchLayoutEdit(true);
        assertFalse(controls.hasCustomLayout());
    }

    @Test public void hostsWithoutTouchRegistrationKeepTheirExistingMenu() throws Exception {
        FrostFrontendView frontend = frontend(new FrameLayout(context), null, new ArrayList<>());
        frontend.open(); frontend.editTouchLayout();
        assertTrue(frontend.isOpen()); assertFalse(frontend.isEditingTouchLayout());
        Field items = FrostFrontendView.class.getDeclaredField("items"); items.setAccessible(true);
        assertTrue(((List<?>) items.get(frontend)).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class) public void duplicateControlIdsAreRejected() {
        controls("one", "duplicate", "duplicate");
    }
}

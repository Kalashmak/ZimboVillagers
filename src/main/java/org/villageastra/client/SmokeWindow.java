package org.villageastra.client;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
/** Dev smoke harness only: a minimized framebuffer is 1x1 and cannot be visual evidence. */
final class SmokeWindow {
 private SmokeWindow(){}
 static boolean ready(Minecraft mc){
  if(mc.getMainRenderTarget().width>=100&&mc.getMainRenderTarget().height>=100)return true;
  long window=mc.getWindow().getWindow();GLFW.glfwRestoreWindow(window);GLFW.glfwShowWindow(window);GLFW.glfwSetWindowSize(window,854,480);return false;
 }
}

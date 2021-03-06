/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.dag.nodes;

import org.terasology.assets.ResourceUrn;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.registry.In;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.dag.ConditionDependentNode;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.READ_ONLY_GBUFFER;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;
import static org.terasology.rendering.opengl.ScalingFactors.FULL_SCALE;
import org.terasology.rendering.opengl.fbms.DisplayResolutionDependentFBOs;
import org.terasology.rendering.world.WorldRenderer;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.terasology.rendering.opengl.OpenGLUtils.bindDisplay;
import static org.terasology.rendering.opengl.OpenGLUtils.renderFullscreenQuad;
import static org.terasology.rendering.opengl.OpenGLUtils.setViewportToSizeOf;

/**
 * TODO: Add diagram of this node
 */
public class AmbientOcclusionPassesNode extends ConditionDependentNode {
    public static final ResourceUrn SSAO = new ResourceUrn("engine:ssao");
    public static final ResourceUrn SSAO_BLURRED = new ResourceUrn("engine:ssaoBlurred");

    @In
    private DisplayResolutionDependentFBOs displayResolutionDependentFBOs;

    @In
    private WorldRenderer worldRenderer;

    @In
    private Config config;

    private RenderingConfig renderingConfig;
    private FBO ssaoBlurredFBO;
    private FBO ssaoFBO;
    private Material ssaoShader;
    private Material ssaoBlurredShader;

    @Override
    public void initialise() {
        renderingConfig = config.getRendering();
        ssaoShader = worldRenderer.getMaterial("engine:prog.ssao");
        ssaoBlurredShader = worldRenderer.getMaterial("engine:prog.ssaoBlur");

        renderingConfig.subscribe(renderingConfig.SSAO, this);
        requiresCondition(() -> renderingConfig.isSsao());
        requiresFBO(new FBOConfig(SSAO, FULL_SCALE, FBO.Type.DEFAULT), displayResolutionDependentFBOs);
        requiresFBO(new FBOConfig(SSAO_BLURRED, FULL_SCALE, FBO.Type.DEFAULT), displayResolutionDependentFBOs);
    }

    /**
     * If Ambient Occlusion is enabled in the render settings, this method generates and
     * stores the necessary images into their own FBOs. The stored images are eventually
     * combined with others.
     * <p>
     * For further information on Ambient Occlusion see: http://en.wikipedia.org/wiki/Ambient_occlusion
     */
    @Override
    public void process() {
        PerformanceMonitor.startActivity("rendering/ambientOcclusionPasses");
        // TODO: consider moving these into initialise without breaking existing implementation
        ssaoBlurredFBO = displayResolutionDependentFBOs.get(SSAO_BLURRED);
        ssaoFBO = displayResolutionDependentFBOs.get(SSAO);

        // TODO: separate this node into two nodes with using methods below
        generateSSAO();
        generateBlurredSSAO();
        PerformanceMonitor.endActivity();
    }

    private void generateSSAO() {
        ssaoShader.enable();
        ssaoShader.setFloat2("texelSize", 1.0f / ssaoFBO.width(), 1.0f / ssaoFBO.height(), true);
        ssaoShader.setFloat2("noiseTexelSize", 1.0f / 4.0f, 1.0f / 4.0f, true);

        // TODO: verify if some textures should be bound here
        ssaoFBO.bind();

        setViewportToSizeOf(ssaoFBO);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary

        renderFullscreenQuad();

        bindDisplay(); // TODO: verify this is necessary
        setViewportToSizeOf(READ_ONLY_GBUFFER); // TODO: verify this is necessary
    }

    private void generateBlurredSSAO() {
        ssaoBlurredShader.enable();
        ssaoBlurredShader.setFloat2("texelSize", 1.0f / ssaoBlurredFBO.width(), 1.0f / ssaoBlurredFBO.height(), true);

        ssaoFBO.bindTexture(); // TODO: verify this is the only input

        ssaoBlurredFBO.bind();

        setViewportToSizeOf(ssaoBlurredFBO);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary

        renderFullscreenQuad();

        bindDisplay(); // TODO: verify this is necessary
        setViewportToSizeOf(READ_ONLY_GBUFFER); // TODO: verify this is necessary
    }
}

package com.takusemba.rtmppublisher;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;

public class RtmpPublisher implements Publisher, SurfaceTexture.OnFrameAvailableListener,
        CameraSurfaceRenderer.OnRendererStateChangedListener, LifecycleObserver {

    public static final int DEFAULT_WIDTH = 720;
    public static final int DEFAULT_HEIGHT = 1280;

    public static final int DEFAULT_AUDIO_BITRATE = 6400;
    public static final int DEFAULT_VIDEO_BITRATE = 100000;

    private GLSurfaceView glView;
    private CameraSurfaceRenderer renderer;
    private CameraClient camera;
    private Streamer streamer;
    private PublisherListener listener;

    public RtmpPublisher(){
        this.streamer = new Streamer();
    }

    @Override
    public void setOnPublisherListener(PublisherListener listener) {
        this.listener = listener;
        this.streamer.setMuxerListener(listener);
    }

    @Override
    public void initialize(AppCompatActivity activity, GLSurfaceView glView) {
        initialize(activity, glView, CameraMode.BACK);
    }

    @Override
    public void initialize(AppCompatActivity activity, GLSurfaceView glView, CameraMode mode) {
        activity.getLifecycle().addObserver(this);

        this.glView = glView;
        this.camera = new CameraClient(activity, mode);

        glView.setEGLContextClientVersion(2);
        renderer = new CameraSurfaceRenderer();
        renderer.addOnRendererStateChangedLister(streamer.getVideoHandlerListener());
        renderer.addOnRendererStateChangedLister(this);

        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void switchCamera() {
        camera.swap();
    }

    @Override
    public void startPublishing(String url) {
        startPublishing(url, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @Override
    public void startPublishing(String url, int width, int height) {
        startPublishing(url, width, height, DEFAULT_AUDIO_BITRATE, DEFAULT_VIDEO_BITRATE);
    }

    @Override
    public void startPublishing(String url, final int width, final int height, final int audioBitrate,
                                final int videoBitrate) {
        streamer.open(url, width, height);
        if (listener != null) listener.onStarted();
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                // EGL14.eglGetCurrentContext() should be called from glView thread.
                final EGLContext context = EGL14.eglGetCurrentContext();
                glView.post(new Runnable() {
                    @Override
                    public void run() {
                        // back to main thread
                        streamer.startStreaming(context, width, height, audioBitrate, videoBitrate);
                    }
                });
            }
        });
    }

    @Override
    public void stopPublishing() {
        if (streamer.isStreaming()) {
            streamer.stopStreaming();
            if (listener != null) listener.onStopped();
        }
    }

    @Override
    public boolean isPublishing() {
        return streamer.isStreaming();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume(LifecycleOwner owner) {
        Camera.Parameters params = camera.open();
        final Camera.Size size = params.getPreviewSize();
        glView.onResume();
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.setCameraPreviewSize(size.width, size.height);
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause(LifecycleOwner owner) {
        if (camera != null) {
            camera.close();
        }
        glView.onPause();
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.pause();
            }
        });
        if (streamer.isStreaming()) {
            streamer.stopStreaming();
        }
    }

    @Override
    public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
        surfaceTexture.setOnFrameAvailableListener(this);
        camera.startPreview(surfaceTexture);
    }

    @Override
    public void onFrameDrawn(int textureId, float[] transform, long timestamp) {
        // no-op
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        glView.requestRender();
    }
}

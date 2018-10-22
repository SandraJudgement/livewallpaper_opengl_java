package com.example.livewallpaper;

import android.graphics.PixelFormat;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.io.InputStream;

import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_CONTEXT_LOST;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_DEPTH_SIZE;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_NO_DISPLAY;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.EGL_RENDERABLE_TYPE;
import static android.opengl.EGL14.EGL_SURFACE_TYPE;
import static android.opengl.EGL14.EGL_WINDOW_BIT;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglCreateWindowSurface;
import static android.opengl.EGL14.eglDestroyContext;
import static android.opengl.EGL14.eglDestroySurface;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglGetError;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.EGL14.eglSwapBuffers;
import static android.opengl.EGL14.eglTerminate;
import static android.opengl.GLES32.GL_ARRAY_BUFFER;
import static android.opengl.GLES32.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES32.GL_ELEMENT_ARRAY_BUFFER;
import static android.opengl.GLES32.GL_FLOAT;
import static android.opengl.GLES32.GL_STATIC_DRAW;
import static android.opengl.GLES32.GL_TRIANGLES;
import static android.opengl.GLES32.GL_UNSIGNED_INT;
import static android.opengl.GLES32.glBindBuffer;
import static android.opengl.GLES32.glClear;
import static android.opengl.GLES32.glClearColor;
import static android.opengl.GLES32.glDeleteProgram;
import static android.opengl.GLES32.glDisableVertexAttribArray;
import static android.opengl.GLES32.glDrawElements;
import static android.opengl.GLES32.glEnableVertexAttribArray;
import static android.opengl.GLES32.glFlush;
import static android.opengl.GLES32.glGetUniformLocation;
import static android.opengl.GLES32.glUniformMatrix4fv;
import static android.opengl.GLES32.glUseProgram;
import static android.opengl.GLES32.glVertexAttribPointer;
import static android.opengl.GLES32.glViewport;

public class MainWallpaper extends WallpaperService {

    public static final String TAG = MainWallpaper.class.getSimpleName();

    @Override
    public Engine onCreateEngine() {
        return new MainWallpaperEngine();
    }

    public class MainWallpaperEngine extends Engine {

        private static final int INPUT_POSITION = 0;
        private static final int INPUT_COLOR = 1;

        private EGLDisplay eglDisplay = EGL_NO_DISPLAY;
        private EGLSurface eglSurface = EGL_NO_SURFACE;
        private EGLContext eglContext = EGL_NO_CONTEXT;

        private HandlerThread renderingThread;
        private Handler renderingHandle;
        private final Object lock = new Object();

        private float width = 0;
        private float height = 0;

        private int shaderProgram = -1;
        private int uniformModelMatrix = -1;
        private int modelVertices = -1;
        private int modelIndices = -1;
        private float rot = 0.0f;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            Log.d(TAG, "onCreate");

            // EGLを初期化
            EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
            int[] major = new int[1];
            int[] minor = new int[1];
            boolean status = eglInitialize(eglDisplay, major, 0, minor, 0);
            if (status) {
                this.eglDisplay = eglDisplay;
            } else {
                Log.e(TAG, "Failed to initialize EGL.");
            }

            // レンダリング用のスレッドを生成
            renderingThread = new HandlerThread(toString());
            renderingThread.start();
            renderingHandle = new Handler(renderingThread.getLooper());

            // 初期化しやすいようにピクセルフォーマットを固定値で設定しておく
            holder.setFormat(PixelFormat.RGB_565);

        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            Log.d(TAG, "onSurfaceCreated");
        }

        @Override
        public void onSurfaceChanged(final SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            Log.d(TAG, "onSurfaceChanged");

            this.width = width;
            this.height = height;

            synchronized (lock) {
                renderingHandle.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) {
                            // EGL関連の初期化
                            if (!initializeEGL(holder)) {
                                return;
                            }

                            // OpenGL関連の初期化
                            initializeGL();
                            drawGL();

                            lock.notify();
                        }
                    }
                });

                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            Log.d(TAG, "onSurfaceRedrawNeeded");
            renderingHandle.post(new Runnable() {
                @Override
                public void run() {
                    drawGL();
                }
            });
        }

        @Override
        public void onOffsetsChanged(final float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
            Log.d(TAG, "onOffsetsChanged");

            renderingHandle.post(new Runnable() {
                @Override
                public void run() {
                    rot = 360.0f * xOffset;
                    drawGL();
                }
            });
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            Log.d(TAG, "onSurfaceDestroyed");

            synchronized (lock) {
                renderingHandle.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (lock) {
                            terminateEGL();
                            lock.notify();
                        }
                    }
                });

                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onDestroy() {
            Log.d(TAG, "onDestroy");
            super.onDestroy();

            renderingThread.quit();

            // EGLを開放
            if (eglDisplay != EGL_NO_DISPLAY) {
                eglTerminate(eglDisplay);
                eglDisplay = EGL_NO_DISPLAY;
            }
        }

        private boolean initializeEGL(SurfaceHolder holder) {
            Log.d(TAG, "initializeEGL");

            eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

            // すでにEGL関連のインスタンスがある場合は開放
            if (eglSurface != EGL_NO_SURFACE) {
                eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = EGL_NO_SURFACE;
            }
            if (eglContext != EGL_NO_CONTEXT) {
                eglDestroyContext(eglDisplay, eglContext);
                eglContext = EGL_NO_CONTEXT;
            }

            // EGL configurationを取得
            int[] configAttrs = new int[]{
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_RED_SIZE, 5,
                    EGL_GREEN_SIZE, 6,
                    EGL_BLUE_SIZE, 5,
                    EGL_DEPTH_SIZE, 16,
                    EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                    EGL_NONE
            };
            EGLConfig[] config = new EGLConfig[1];
            int[] numConfigs = new int[1];
            boolean status = EGL14.eglChooseConfig(eglDisplay, configAttrs, 0, config, 0, 1, numConfigs, 0);
            if (!status || numConfigs[0] == 0) {
                Log.e(TAG, "Failed to choose a EGL configuration.");
                return false;
            }

            // EGL surfaceの生成
            eglSurface = eglCreateWindowSurface(eglDisplay, config[0], holder.getSurface(), null, 0);
            if (eglSurface == EGL_NO_SURFACE) {
                Log.e(TAG, "Failed to create a EGL window surface.");
                return false;
            }

            // EGL contextの生成
            int[] contextAttrs = new int[]{
                    EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL_NONE
            };
            eglContext = eglCreateContext(eglDisplay, config[0], EGL_NO_CONTEXT, contextAttrs, 0);
            if (eglContext == EGL_NO_CONTEXT) {
                Log.e(TAG, "Failed to create a EGL context.");
                return false;
            }

            // EGLをバインドしてOpenGLを有効化する
            return eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
        }

        private void terminateEGL() {
            Log.d(TAG, "terminateEGL");

            // OpenGL関連の開放
            terminateGL();

            // EGL関連の開放
            eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);

            if (eglSurface != EGL_NO_SURFACE) {
                eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = EGL_NO_SURFACE;
            }
            if (eglContext != EGL_NO_CONTEXT) {
                eglDestroyContext(eglDisplay, eglContext);
                eglContext = EGL_NO_CONTEXT;
            }
        }

        private void initializeGL() {
            Log.d(TAG, "initializeGL");

            // ステータス初期化
            glViewport(0, 0, (int) width, (int) height);
            glClearColor(0.5f, 0.5f, 0.5f, 1.0f);

            // シェーダプログラム生成
            try (InputStream vs = getAssets().open("shader.vert");
                 InputStream fs = getAssets().open("shader.frag")) {
                shaderProgram = GLES32Utils.createProgram(vs, fs);
                uniformModelMatrix = glGetUniformLocation(shaderProgram, "uModelMatrix");
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 描画物生成、X, Y, Z, R, G, B, A
            modelVertices = GLES32Utils.createBuffer(GL_ARRAY_BUFFER, new float[]{
                    0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
                    (float) Math.sin(2.0 * Math.PI / 3), (float) Math.cos(2.0 * Math.PI / 3), 0.0f, 0.0f, 1.0f, 0.0f, 1.0f,
                    (float) Math.sin(-2.0 * Math.PI / 3), (float) Math.cos(-2.0 * Math.PI / 3), 0.0f, 0.0f, 0.0f, 1.0f, 1.0f,
            }, GL_STATIC_DRAW);
            modelIndices = GLES32Utils.createBuffer(GL_ELEMENT_ARRAY_BUFFER, new int[]{
                    0, 1, 2
            }, GL_STATIC_DRAW);
        }

        private void drawGL() {
            // コンテキストのロストチェック
            if (eglGetError() == EGL_CONTEXT_LOST) {
                if (!initializeEGL(getSurfaceHolder())) {
                    return;
                }
                initializeGL();
            }

            // 描画処理
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(shaderProgram);

            float[] modelMatrix = new float[16];
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.scaleM(modelMatrix, 0, 1.0f, width / height, 1.0f);
            Matrix.rotateM(modelMatrix, 0, rot, 0.0f, 0.0f, 1.0f);
            glUniformMatrix4fv(uniformModelMatrix, 1, false, modelMatrix, 0);

            glEnableVertexAttribArray(INPUT_POSITION);
            glEnableVertexAttribArray(INPUT_COLOR);

            glBindBuffer(GL_ARRAY_BUFFER, modelVertices);
            glVertexAttribPointer(INPUT_POSITION, 3, GL_FLOAT, false, 4 * 7, 0);
            glVertexAttribPointer(INPUT_COLOR, 4, GL_FLOAT, false, 4 * 7, 4 * 3);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, modelIndices);
            glDrawElements(GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0);

            glDisableVertexAttribArray(INPUT_POSITION);
            glDisableVertexAttribArray(INPUT_COLOR);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glUseProgram(0);

            glFlush();

            // 描画サーフェスのスワップ
            eglSwapBuffers(eglDisplay, eglSurface);
        }

        private void terminateGL() {
            GLES32Utils.deleteBuffer(modelVertices);
            GLES32Utils.deleteBuffer(modelIndices);
            glDeleteProgram(shaderProgram);
        }
    }
}

package com.example.livewallpaper;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.opengl.GLES32.GL_FALSE;
import static android.opengl.GLES32.GL_FRAGMENT_SHADER;
import static android.opengl.GLES32.GL_INVALID_ENUM;
import static android.opengl.GLES32.GL_INVALID_FRAMEBUFFER_OPERATION;
import static android.opengl.GLES32.GL_INVALID_OPERATION;
import static android.opengl.GLES32.GL_INVALID_VALUE;
import static android.opengl.GLES32.GL_LINK_STATUS;
import static android.opengl.GLES32.GL_NO_ERROR;
import static android.opengl.GLES32.GL_OUT_OF_MEMORY;
import static android.opengl.GLES32.GL_VERTEX_SHADER;
import static android.opengl.GLES32.glAttachShader;
import static android.opengl.GLES32.glBindBuffer;
import static android.opengl.GLES32.glBufferData;
import static android.opengl.GLES32.glCompileShader;
import static android.opengl.GLES32.glCreateProgram;
import static android.opengl.GLES32.glCreateShader;
import static android.opengl.GLES32.glDeleteBuffers;
import static android.opengl.GLES32.glDeleteProgram;
import static android.opengl.GLES32.glDeleteShader;
import static android.opengl.GLES32.glGenBuffers;
import static android.opengl.GLES32.glGetError;
import static android.opengl.GLES32.glGetProgramInfoLog;
import static android.opengl.GLES32.glGetProgramiv;
import static android.opengl.GLES32.glLinkProgram;
import static android.opengl.GLES32.glShaderSource;

public class GLES32Utils {

	private static String TAG = "GLES32Utils";

	public static void printError() {
		int error = glGetError();
		switch (error) {
			case GL_NO_ERROR:
				break;

			case GL_INVALID_ENUM:
				Log.d(TAG, "GL_INVALID_ENUM");
				break;

			case GL_INVALID_VALUE:
				Log.d(TAG, "GL_INVALID_VALUE");
				break;

			case GL_INVALID_OPERATION:
				Log.d(TAG, "GL_INVALID_OPERATION");
				break;

			case GL_INVALID_FRAMEBUFFER_OPERATION:
				Log.d(TAG, "GL_INVALID_FRAMEBUFFER_OPERATION");
				break;

			case GL_OUT_OF_MEMORY:
				Log.d(TAG, "GL_OUT_OF_MEMORY");
				break;

			default:
				Log.d(TAG, "Unknown error. Error code is " + error);
				break;
		}
	}

	public static int createBuffer() {
		int[] buffer = new int[1];
		glGenBuffers(1, buffer, 0);
		return buffer[0];
	}

	public static int createBuffer(int target, ByteBuffer data, int usage) {
		int buffer = createBuffer();
		glBindBuffer(target, buffer);
		glBufferData(target, data.remaining(), data, usage);
		return buffer;
	}

	public static int createBuffer(int target, int[] data, int usage) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * data.length).order(ByteOrder.nativeOrder());
		buffer.asIntBuffer().put(data).position(0);
		return createBuffer(target, buffer, usage);
	}

	public static int createBuffer(int target, float[] data, int usage) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(4 * data.length).order(ByteOrder.nativeOrder());
		buffer.asFloatBuffer().put(data).position(0);
		return createBuffer(target, buffer, usage);
	}

	public static void deleteBuffer(int buffer) {
		glDeleteBuffers(1, new int[]{buffer}, 0);
	}

	public static int createShader(int type, String code) {
		int shader = glCreateShader(type);
		glShaderSource(shader, code);
		glCompileShader(shader);
		return shader;
	}

	public static int createShader(int type, InputStream stream) {
		return createShader(type, readString(stream));
	}

	public static int createProgram(String vertexShader, String fragmentShader) {
		int vs = createShader(GL_VERTEX_SHADER, vertexShader);
		if (vs == -1) {
			return -1;
		}

		int fs = createShader(GL_FRAGMENT_SHADER, fragmentShader);
		if (fs == -1) {
			glDeleteShader(vs);
			return -1;
		}

		int program = glCreateProgram();
		glAttachShader(program, vs);
		glAttachShader(program, fs);
		glLinkProgram(program);
		glDeleteShader(vs);
		glDeleteShader(fs);
		int[] status = new int[1];
		glGetProgramiv(program, GL_LINK_STATUS, status, 0);
		if (status[0] == GL_FALSE) {
			Log.e(TAG, glGetProgramInfoLog(program));
			glDeleteProgram(program);
		}
		return program;
	}

	public static int createProgram(InputStream vertexShader, InputStream fragmentShader) {
		return createProgram(readString(vertexShader), readString(fragmentShader));
	}

	private static String readString(InputStream stream) {
		try {
			Reader reader = new InputStreamReader(stream, "UTF-8");
			StringBuilder builder = new StringBuilder();
			char[] buffer = new char[1024];
			int read;
			while ((read = reader.read(buffer)) != -1) {
				builder.append(buffer, 0, read);
			}
			return builder.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
}

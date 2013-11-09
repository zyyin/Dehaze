package com.example.dehaze;

import android.app.Application;

import com.parse.Parse;

public class DehazeApplication extends Application {
	@Override
	public void onCreate() {
		Parse.initialize(this, "rd6jYwmVzLoNcyU908IXwFkdSyCkm3nakw5iqwVr",
				"sFQpA4QwFEuWq0KhxCRGdo4CpMarYIxFVkPLUWAm");
	}
}

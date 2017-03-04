package com.zhao.audiohost;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;import android.os.Handler;
public class Welcome extends Activity  
{  
  
    @Override  
    protected void onCreate(Bundle savedInstanceState)  
    {  
        super.onCreate(savedInstanceState);  
        setContentView(R.layout.welcome);  
        new Handler().postDelayed(new Runnable()  
        {  
  
            @Override  
            public void run()  
            {  
                // TODO Auto-generated method stub  
                Intent intent=new Intent(); 
                intent.setClassName(getApplication(), "com.zhao.audiohost.MainActivity");
                startActivity(intent);  
                Welcome.this.finish();  
            }  
        }, 1000);  
    }  
  
      
  
} 
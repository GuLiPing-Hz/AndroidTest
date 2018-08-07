package com.tt.androidtest;

import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainActivity";

    int mCnt = 0;

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        log("onCreate");
//        testRx();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                log("onCreate run");
//                testRx();
//            }
//        }).start();

        testRx2();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    synchronized void log(String s) {
        Log.i(TAG, String.format("%03d %s : %s", mCnt++, Thread.currentThread().toString(), s));
    }

    void testRx() {
        log("testRx");

        Observable
                .create(new ObservableOnSubscribe<String>() {
                    @Override
                    public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                        log("create subscribe");
                        emitter.onNext("1");
                        emitter.onNext("2");
                        emitter.onNext("3");
                        emitter.onNext("4");
                        emitter.onNext("5");
                        emitter.onComplete();
                    }
                })
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        log("doOnSubscribe");
                    }
                })
                .subscribeOn(Schedulers.newThread())//设置在事件监听之前，只有第一次设置有效
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(new Function<String, Integer>() {
                    @Override
                    public Integer apply(String s) throws Exception {
                        log("map1 " + s);
                        return Integer.parseInt(s);
                    }
                })
                .observeOn(Schedulers.computation())
                .map(new Function<Integer, String>() {
                    @Override
                    public String apply(Integer integer) throws Exception {
                        log("map2 " + integer);
                        return "integer " + integer;
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //这里的执行线程跟被观察者创建在什么线程有关
                        log("subscribe");
                    }

                    @Override
                    public void onNext(String s) {
                        log("onNext " + s);
                    }

                    @Override
                    public void onError(Throwable e) {
                        log("onError " + e.toString());
                    }

                    @Override
                    public void onComplete() {
                        log("onComplete ");
                    }
                });
    }

    public class Teacher {
        public String name;
        public List<Integer> clss;

        public Teacher(String name, List<Integer> clss) {
            this.name = name;
            this.clss = clss;
        }
    }

    void testRx2() {
        List<Integer> ll = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ll.add(100 + i);
        }

        Teacher teacher = new Teacher("Jack", ll);
        Observable.just(teacher)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.newThread())
                .flatMap(new Function<Teacher, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(final Teacher teacher) throws Exception {
                        log("flatMap1 apply" + teacher.toString());


                        return Observable.create(new ObservableOnSubscribe<Integer>() {
                            @Override
                            public void subscribe(ObservableEmitter<Integer> emitter) throws Exception {
                                for (Integer cls : teacher.clss) {
                                    emitter.onNext(cls);
                                }
                                emitter.onComplete();
                            }
                        });

                        //上面的创建可以看做是下面这个函数的简化版，原理差不多
//                        return Observable.fromIterable(teacher.clss);
                    }
                })
                .filter(new Predicate<Integer>() {
                    @Override
                    public boolean test(Integer integer) throws Exception {
                        log("test " + integer);
                        return integer > 104;
                    }
                })
                //flatMap(new Function<Integer, ObservableSource<Integer>>() {//执行顺序不一定,像下面这样，某个元素比较费时 105会在最后执行
                .concatMap(new Function<Integer, ObservableSource<Integer>>() {//执行顺序一定，会等到105完成后继续后面的元素
                    @Override
                    public ObservableSource<Integer> apply(Integer integer) throws Exception {
                        log("flatMap/concatMap apply" + integer);
                        //延迟发送
                        return Observable.just(integer).delay(integer == 105 ? 1 : 0, TimeUnit.SECONDS);
                    }
                })
                .subscribe(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer integer) throws Exception {
                        log("accept " + integer);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        log("accept " + throwable.toString());
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        log("accept Action");
                    }
                }, new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        log("accept disposable");
                    }
                }).isDisposed();
    }
}

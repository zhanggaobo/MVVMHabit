package com.goldze.mvvmhabit.ui.network;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableList;
import android.support.annotation.NonNull;

import com.goldze.mvvmhabit.BR;
import com.goldze.mvvmhabit.R;
import com.goldze.mvvmhabit.data.DemoRepository;
import com.goldze.mvvmhabit.entity.DemoEntity;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import me.goldze.mvvmhabit.base.BaseViewModel;
import me.goldze.mvvmhabit.binding.command.BindingAction;
import me.goldze.mvvmhabit.binding.command.BindingCommand;
import me.goldze.mvvmhabit.bus.event.SingleLiveEvent;
import me.goldze.mvvmhabit.http.BaseResponse;
import me.goldze.mvvmhabit.http.ResponseThrowable;
import me.goldze.mvvmhabit.utils.RxUtils;
import me.goldze.mvvmhabit.utils.ToastUtils;
import me.tatarka.bindingcollectionadapter2.BindingRecyclerViewAdapter;
import me.tatarka.bindingcollectionadapter2.ItemBinding;

/**
 * Created by goldze on 2017/7/17.
 */

public class NetWorkViewModel extends BaseViewModel<DemoRepository> {
    public SingleLiveEvent<NetWorkItemViewModel> deleteItemLiveData = new SingleLiveEvent<>();
    //封装一个界面发生改变的观察者
    public UIChangeObservable uc = new UIChangeObservable();

    public class UIChangeObservable {
        //下拉刷新完成
        public ObservableBoolean finishRefreshing = new ObservableBoolean(false);
        //上拉加载完成
        public ObservableBoolean finishLoadmore = new ObservableBoolean(false);
    }

    public NetWorkViewModel(@NonNull Application application, DemoRepository repository) {
        super(application, repository);
    }

    //给RecyclerView添加ObservableList
    public ObservableList<NetWorkItemViewModel> observableList = new ObservableArrayList<>();
    //给RecyclerView添加ItemBinding
    public ItemBinding<NetWorkItemViewModel> itemBinding = ItemBinding.of(BR.viewModel, R.layout.item_network);
    //给RecyclerView添加Adpter，请使用自定义的Adapter继承BindingRecyclerViewAdapter，重写onBindBinding方法，里面有你要的Item对应的binding对象
    public final BindingRecyclerViewAdapter<NetWorkItemViewModel> adapter = new BindingRecyclerViewAdapter<>();
    //下拉刷新
    public BindingCommand onRefreshCommand = new BindingCommand(new BindingAction() {
        @Override
        public void call() {
            ToastUtils.showShort("下拉刷新");
            requestNetWork();
        }
    });
    //上拉加载
    public BindingCommand onLoadMoreCommand = new BindingCommand(new BindingAction() {
        @Override
        public void call() {
            if (observableList.size() > 50) {
                ToastUtils.showLong("兄dei，你太无聊啦~崩是不可能的~");
                uc.finishLoadmore.set(!uc.finishLoadmore.get());
                return;
            }
            //模拟网络上拉加载更多
            addSubscribe(model.simulationLoadMore()
                    .compose(RxUtils.schedulersTransformer()) //线程调度
                    .doOnSubscribe(new Consumer<Disposable>() {
                        @Override
                        public void accept(Disposable disposable) throws Exception {
                            ToastUtils.showShort("上拉加载");
                        }
                    })
                    .subscribe(new Consumer<DemoEntity>() {
                        @Override
                        public void accept(DemoEntity entity) throws Exception {
                            for (DemoEntity.ItemsEntity itemsEntity : entity.getItems()) {
                                NetWorkItemViewModel itemViewModel = new NetWorkItemViewModel(NetWorkViewModel.this, itemsEntity);
                                //双向绑定动态添加Item
                                observableList.add(itemViewModel);
                            }
                            //刷新完成收回
                            uc.finishLoadmore.set(!uc.finishLoadmore.get());
                        }
                    }));
        }
    });

    /**
     * 网络请求方法，在ViewModel中调用Model层，通过Okhttp+Retrofit+RxJava发起请求
     */
    public void requestNetWork() {
        //建议使用addSubscribe()套一层，请求与View周期同步
        //addSubscribe();
        model.demoGet()
                .compose(RxUtils.bindToLifecycle(getLifecycleProvider())) //请求与View周期同步（过度期，尽量少使用）
                .compose(RxUtils.schedulersTransformer()) //线程调度
                .compose(RxUtils.exceptionTransformer()) // 网络错误的异常转换, 这里可以换成自己的ExceptionHandle
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        showDialog("正在请求...");
                    }
                })
                .subscribe(new Consumer<BaseResponse<DemoEntity>>() {
                    @Override
                    public void accept(BaseResponse<DemoEntity> response) throws Exception {
                        //清除列表
                        observableList.clear();
                        //请求成功
                        if (response.getCode() == 1) {
                            for (DemoEntity.ItemsEntity entity : response.getResult().getItems()) {
                                NetWorkItemViewModel itemViewModel = new NetWorkItemViewModel(NetWorkViewModel.this, entity);
                                //双向绑定动态添加Item
                                observableList.add(itemViewModel);
                            }
                        } else {
                            //code错误时也可以定义Observable回调到View层去处理
                            ToastUtils.showShort("数据错误");
                        }
                    }
                }, new Consumer<ResponseThrowable>() {
                    @Override
                    public void accept(ResponseThrowable throwable) throws Exception {
                        //关闭对话框
                        dismissDialog();
                        //请求刷新完成收回
                        uc.finishRefreshing.set(!uc.finishRefreshing.get());
                        ToastUtils.showShort(throwable.message);
                    }
                }, new Action() {
                    @Override
                    public void run() throws Exception {
                        //关闭对话框
                        dismissDialog();
                        //请求刷新完成收回
                        uc.finishRefreshing.set(!uc.finishRefreshing.get());
                    }
                });
    }

    /**
     * 删除条目
     *
     * @param netWorkItemViewModel
     */
    public void deleteItem(NetWorkItemViewModel netWorkItemViewModel) {
        //点击确定，在 observableList 绑定中删除，界面立即刷新
        observableList.remove(netWorkItemViewModel);
    }

    /**
     * 获取条目下标
     *
     * @param netWorkItemViewModel
     * @return
     */
    public int getPosition(NetWorkItemViewModel netWorkItemViewModel) {
        return observableList.indexOf(netWorkItemViewModel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}

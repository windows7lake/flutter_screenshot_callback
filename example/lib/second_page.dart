import 'package:flutter/material.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:image_downloader/image_downloader.dart';
import 'package:screenshot_callback/screenshot_callback.dart';

class SecondPage extends StatefulWidget {
  @override
  _SecondPageState createState() => _SecondPageState();
}

class _SecondPageState extends State<SecondPage> {
  ScreenshotCallback screenshotCallback;

  String text = "Ready..";
  int count = 0;
  bool fired = true;

  @override
  void initState() {
    super.initState();
    init();
  }

  void init() async {
    await initScreenshotCallback();
  }

  //It must be created after permission is granted.
  Future<void> initScreenshotCallback() async {
    screenshotCallback = ScreenshotCallback();

    screenshotCallback.addListener(() {
      if (!fired) return;
      setState(() {
        count++;
        text = "Screenshot callback Fired! $count";
      });
    });

    screenshotCallback.addListener(() {
      print("We can add multiple listeners ");
      // Fluttertoast.cancel();
      // Fluttertoast.showToast(
      //     msg: "Screenshot callback Fired!",
      //     toastLength: Toast.LENGTH_SHORT,
      //     gravity: ToastGravity.CENTER,
      //     timeInSecForIosWeb: 1,
      //     backgroundColor: Colors.red,
      //     textColor: Colors.white,
      //     fontSize: 16.0);
    });
  }

  @override
  void dispose() {
    screenshotCallback.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Detect Screenshot Callback Example'),
        ),
        body: Center(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                text,
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                ),
              ),
              FlatButton(
                onPressed: () async {
                  fired = false;
                  var imageId = await ImageDownloader.downloadImage(
                    "https://cp4.100.com.tw/images/articles/202007/21/admin_30_1595329480_WybKjrUp3l.png!t1000.webp",
                    outputMimeType: "image/png",
                    destination: AndroidDestinationType.directoryDCIM,
                  );
                  fired = true;
                },
                child: Text('下载'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

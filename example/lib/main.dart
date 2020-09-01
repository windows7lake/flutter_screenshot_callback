import 'package:flutter/material.dart';
import 'package:screenshot_callback_example/second_page.dart';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: HomePage(),
    );
  }
}

class HomePage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Detect Screenshot Callback Example'),
      ),
      body: Center(
        child: FlatButton(
          onPressed: () {
            print(">>>>>>>>>>>");
            Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => SecondPage()),
            );
          },
          child: Text("jump"),
        ),
      ),
    );
  }
}

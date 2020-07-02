// IHMIListener.aidl
package dcv.finaltest.hmiapplication;

// Declare any non-default types here with import statements


interface IHMIListener {
      void onDistanceUnitChanged(int distanceUnit);
      void onDistanceChanged(double distance);
      void OnConsumptionUnitChanged(int consumptionUnit);
      void onConsumptionChanged(in double[] consumptionList);   
      void onError(boolean isError);
}

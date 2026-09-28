package riptide.util.multi;

public interface MultiConnectionMarker {
   boolean riptide$isMultiManaged();

   MultiConnectionContext.ProxySpec riptide$multiProxy();

   void riptide$setMultiManaged(MultiConnectionContext.ProxySpec var1);

   void riptide$clearMultiManaged();
}

DROP TABLE IF EXISTS `buffshop`;
CREATE TABLE `buffshop` (
  `ownerId` int(11) NOT NULL,
  `buffs` varchar(255) NOT NULL default '',
  `title` varchar(255) NOT NULL default '',
  `x` int(7) default NULL,
  `y` int(7) default NULL,
  `z` int(7) default NULL,
  `heading` int(7) default NULL,
  PRIMARY KEY  (`ownerId`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;
